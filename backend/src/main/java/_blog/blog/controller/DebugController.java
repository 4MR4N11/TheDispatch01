package _blog.blog.controller;


import java.util.Arrays;
import java.util.List;

import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DebugController {
    private final ApplicationContext context;

    public DebugController(ApplicationContext context) {
        this.context = context;
    }

    @GetMapping("/debug/beans")
    public List<String> getAllBeans() {
        System.out.println(Arrays.asList(context.getBeanDefinitionNames()));
        return Arrays.asList(context.getBeanDefinitionNames());
    }
}
