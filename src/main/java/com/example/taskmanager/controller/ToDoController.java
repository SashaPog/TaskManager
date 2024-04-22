package com.example.taskmanager.controller;

import com.example.taskmanager.config.CustomUserDetails;
import com.example.taskmanager.model.Task;
import com.example.taskmanager.model.ToDo;
import com.example.taskmanager.model.User;
import com.example.taskmanager.service.RoleService;
import com.example.taskmanager.service.TaskService;
import com.example.taskmanager.service.ToDoService;
import com.example.taskmanager.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/todos")
public class ToDoController {

    private final RoleService roleService;
    private final ToDoService todoService;
    private final TaskService taskService;
    private final UserService userService;

    public ToDoController(RoleService roleService, ToDoService todoService, TaskService taskService, UserService userService) {
        this.roleService = roleService;
        this.todoService = todoService;
        this.taskService = taskService;
        this.userService = userService;
    }

    @GetMapping("/create/users/{owner_id}")
    @PreAuthorize("hasRole('ADMIN') or #ownerId == principal.user.id")
    public String create(@PathVariable("owner_id") long ownerId,
                         Model model) {

        model.addAttribute("todo", new ToDo());
        model.addAttribute("ownerId", ownerId);
        return "create-todo";
    }

    @PostMapping("/create/users/{owner_id}")
    @PreAuthorize("hasRole('ADMIN') or #ownerId == principal.user.id")
    public String create(@PathVariable("owner_id") long ownerId,
                         @Validated @ModelAttribute("todo") ToDo todo,
                         BindingResult result) {

        if (result.hasErrors()) {
            return "create-todo";
        }
        todo.setCreatedAt(LocalDateTime.now());
        todo.setOwner(userService.readById(ownerId));
        todoService.create(todo);
        return "redirect:/todos/all/users/" + ownerId;
    }

    @GetMapping("/{id}/tasks")
    public String read(@PathVariable long id,
                       Authentication authentication,
                       Model model) {

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        ToDo todo = todoService.readById(id);

        if (!isAdmin(userDetails.getUser()) &&
                !isSameUser(todo.getOwner(), userDetails.getUser()) &&
                !isCollaborator(todo.getCollaborators(), userDetails.getUser())) {

            throw new AccessDeniedException("Access Denied");
        }

        List<Task> tasks = taskService.getByTodoId(id);
        List<User> users = userService.getAll().stream()
                .filter(user -> user.getId() != todo.getOwner().getId()).collect(Collectors.toList());
        List<User> notCollaborators = users.stream()
                .filter(user -> !todo.getCollaborators().contains(user)).toList();

        model.addAttribute("todo", todo);
        model.addAttribute("user", userDetails.getUser());
        model.addAttribute("tasks", tasks);
        model.addAttribute("users", users);
        model.addAttribute("notCollaborators", notCollaborators);

        return "todo-tasks";
    }

    @GetMapping("/{todo_id}/update/users/{owner_id}")
    @PreAuthorize("hasRole('ADMIN') or @toDoServiceImpl.readById(#todoId).owner.id == principal.user.id")
    public String update(@PathVariable("todo_id") long todoId,
                         @PathVariable("owner_id") long ownerId,
                         Model model) {
        ToDo todo = todoService.readById(todoId);

        model.addAttribute("todo", todo);
        return "update-todo";
    }

    @PostMapping("/{todo_id}/update/users/{owner_id}")
    @PreAuthorize("hasRole('ADMIN') or @toDoServiceImpl.readById(#todoId).owner.id == principal.user.id")
    public String update(@PathVariable("todo_id") long todoId,
                         @PathVariable("owner_id") long ownerId,
                         @Validated @ModelAttribute("todo") ToDo todo,
                         BindingResult result) {

        if (result.hasErrors()) {
            todo.setOwner(userService.readById(ownerId));
            return "update-todo";
        }
        ToDo oldTodo = todoService.readById(todoId);
        todo.setOwner(oldTodo.getOwner());
        todo.setCollaborators(oldTodo.getCollaborators());
        todoService.update(todo);
        return "redirect:/todos/all/users/" + ownerId;
    }

    @GetMapping("/{todo_id}/delete/users/{owner_id}")
    @PreAuthorize("hasRole('ADMIN') or @toDoServiceImpl.readById(#todoId).owner.id == principal.user.id")
    public String delete(@PathVariable("todo_id") long todoId,
                         @PathVariable("owner_id") long ownerId) {

        todoService.delete(todoId);
        return "redirect:/todos/all/users/" + ownerId;
    }

    @GetMapping("/all/users/{user_id}")
    @PreAuthorize("hasRole('ADMIN') or #userId == principal.user.id")
    public String getAll(@PathVariable("user_id") long userId,
                         Authentication authentication,
                         Model model) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        User owner = userService.readById(userId);
        List<ToDo> todos;
        if("ADMIN".equals(owner.getRole().getName())){
            todos = todoService.getAll();
        } else {
            todos = todoService.getByUserId(userId);
        }

        model.addAttribute("authUser", userDetails.getUser());
        model.addAttribute("todos", todos.stream().filter(t -> isAdmin(userDetails.getUser()) || isSameUser(t.getOwner(), userDetails.getUser()) || isCollaborator(t.getCollaborators(), userDetails.getUser())));
        model.addAttribute("user", owner);
        return "todos-user";
    }

    @GetMapping("/{id}/add")
    @PreAuthorize("hasRole('ADMIN') or @toDoServiceImpl.readById(#id).owner.id == principal.user.id")
    public String addCollaborator(@PathVariable long id,
                                  @RequestParam("user_id") long userId) {

        ToDo todo = todoService.readById(id);

        List<User> collaborators = todo.getCollaborators();
        User foundUser = userService.readById(userId);
        if(!collaborators.contains(foundUser)) {
            collaborators.add(userService.readById(userId));
        }
        todo.setCollaborators(collaborators);
        todoService.update(todo);
        return "redirect:/todos/" + id + "/tasks";
    }

    @GetMapping("/{id}/remove")
    @PreAuthorize("hasRole('ADMIN') or @toDoServiceImpl.readById(#id).owner.id == principal.user.id")
    public String removeCollaborator(@PathVariable long id,
                                     @RequestParam("user_id") long userId) {

        ToDo todo = todoService.readById(id);

        List<User> collaborators = todo.getCollaborators();
        collaborators.remove(userService.readById(userId));
        todo.setCollaborators(collaborators);
        todoService.update(todo);
        return "redirect:/todos/" + id + "/tasks";
    }

    private boolean isAdmin(User user) {
        return user.getRole().getName().equals(roleService.readById(1).getName());
    }

    private boolean isCollaborator(List<User> collaborators, User user) {
        return collaborators.stream().anyMatch(u -> u.getId() == user.getId());
    }

    private boolean isSameUser(User user1, User user2) {
        if (user1 == user2) {
            return true;
        }

        return user1.getId() == user2.getId();
    }
}
