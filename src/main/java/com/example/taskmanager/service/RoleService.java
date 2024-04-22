package com.example.taskmanager.service;

import com.example.taskmanager.model.Role;

import java.util.List;

public interface RoleService {
    Role create(Role role);
    Role readById(long id);
    Role update(Role role);
    void delete(long id);
    List<Role> getAll();
    Role getRoleByName(String roleName);
}
