package com.project.auth.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.entity.Menu;

/** Dựng cây menu từ danh sách phẳng (đã sắp theo sortOrder). */
final class MenuTree {

    private MenuTree() {
    }

    /**
     * @param include menu nào được đưa vào cây; menu bị loại thì cả nhánh con của nó cũng bị loại
     */
    static List<MenuNode> build(List<Menu> menus, Predicate<Menu> include) {
        Map<UUID, List<Menu>> children = new HashMap<>();
        List<Menu> roots = new ArrayList<>();
        for (Menu menu : menus) {
            if (menu.getParentId() == null) {
                roots.add(menu);
            } else {
                children.computeIfAbsent(menu.getParentId(), k -> new ArrayList<>()).add(menu);
            }
        }
        return nodes(roots, children, include);
    }

    private static List<MenuNode> nodes(List<Menu> menus, Map<UUID, List<Menu>> children, Predicate<Menu> include) {
        List<MenuNode> nodes = new ArrayList<>();
        for (Menu m : menus) {
            if (include.test(m)) {
                nodes.add(new MenuNode(m.getId(), m.getCode(), m.getName(), m.getPath(), m.getIcon(),
                        m.getSortOrder(), m.isActive(),
                        nodes(children.getOrDefault(m.getId(), List.of()), children, include)));
            }
        }
        return nodes;
    }
}
