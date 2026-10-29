package com.project.auth.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.project.auth.dto.AuthzDtos.MenuNode;
import com.project.auth.dto.AuthzDtos.Status;
import com.project.auth.entity.Menu;

/** Dựng cây menu từ danh sách phẳng (đã sắp theo orderIndex). */
final class MenuTree {

    private MenuTree() {
    }

    /**
     * @param include menu nào được đưa vào cây; menu bị loại thì cả nhánh con của nó cũng bị loại.
     *                Menu có parentId trỏ tới menu không tồn tại được coi là menu gốc.
     */
    static List<MenuNode> build(List<Menu> menus, Predicate<Menu> include) {
        Map<UUID, Menu> byId = new HashMap<>();
        menus.forEach(m -> byId.put(m.getId(), m));
        Map<UUID, List<Menu>> children = new HashMap<>();
        List<Menu> roots = new ArrayList<>();
        for (Menu menu : menus) {
            if (menu.getParentId() == null || !byId.containsKey(menu.getParentId())) {
                roots.add(menu);
            } else {
                children.computeIfAbsent(menu.getParentId(), k -> new ArrayList<>()).add(menu);
            }
        }
        return nodes(roots, 1, children, include);
    }

    /** Menu và mọi menu con / cháu của nó. */
    static List<Menu> subtree(List<Menu> menus, UUID rootId) {
        List<Menu> result = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        menus.stream().filter(m -> m.getId().equals(rootId)).forEach(result::add);
        seen.add(rootId);
        for (int i = 0; i < result.size(); i++) {
            UUID parent = result.get(i).getId();
            menus.stream().filter(m -> parent.equals(m.getParentId()) && seen.add(m.getId())).forEach(result::add);
        }
        return result;
    }

    private static List<MenuNode> nodes(List<Menu> menus, int level, Map<UUID, List<Menu>> children,
            Predicate<Menu> include) {
        List<MenuNode> nodes = new ArrayList<>();
        for (Menu m : menus) {
            if (include.test(m)) {
                nodes.add(new MenuNode(m.getId(), m.getCode(), m.getName(), m.getUrl(), m.getIcon(),
                        m.getParentId(), m.getOrderIndex(), level, Status.of(m.isActive()),
                        AuthzSeeds.SYSTEM_MENU_CODES.contains(m.getCode()), m.getVersion(),
                        nodes(children.getOrDefault(m.getId(), List.of()), level + 1, children, include)));
            }
        }
        return nodes;
    }
}
