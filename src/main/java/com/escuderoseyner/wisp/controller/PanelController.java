package com.escuderoseyner.wisp.controller;

import com.escuderoseyner.wisp.dto.PanelResponse;
import com.escuderoseyner.wisp.service.PanelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/panel") // /api/admin/**: solo ADMIN (ver SecurityConfig)
public class PanelController {

    private final PanelService panelService;

    public PanelController(PanelService panelService) {
        this.panelService = panelService;
    }

    @GetMapping
    public PanelResponse panel() {
        return panelService.panel();
    }
}
