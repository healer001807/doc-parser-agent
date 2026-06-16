package com.example.docparser.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端页面路由 - 确保 SPA 刷新时正确返回 index.html
 */
@Controller
public class IndexController {

    /**
     * 处理根路径及前端路由，返回 Vue 应用的 index.html
     */
    @GetMapping(value = {
            "/",
            "/index.html"
    })
    public String index() {
        return "forward:/index.html";
    }
}
