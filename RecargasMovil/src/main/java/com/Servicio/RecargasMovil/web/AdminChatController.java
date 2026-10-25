package com.Servicio.RecargasMovil.web;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.Servicio.RecargasMovil.model.AdminChatMessage;
import com.Servicio.RecargasMovil.security.AuthenticatedIdentity;

@Controller
@RequestMapping("/admin/chat")
public class AdminChatController {

    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final int MAX_MESSAGES = 100;
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final MongoTemplate mongoTemplate;
    private final String accesoUrl;

    public AdminChatController(
            MongoTemplate mongoTemplate,
            @Value("${app.acceso.base-url}") String accesoUrl) {
        this.mongoTemplate = mongoTemplate;
        this.accesoUrl = accesoUrl;
    }

    @ModelAttribute("serviciosUrl")
    public String serviciosUrl() {
        return accesoUrl + "/servicios";
    }

    @GetMapping
    public String chat(@AuthenticationPrincipal AuthenticatedIdentity identity, Model model) {
        requireAdminOrModerator(identity);
        model.addAttribute("identity", identity);
        return "admin-chat";
    }

    @GetMapping("/mensajes")
    @ResponseBody
    public List<MessageResponse> messages(@AuthenticationPrincipal AuthenticatedIdentity identity) {
        requireAdminOrModerator(identity);
        Query query = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(MAX_MESSAGES);
        List<AdminChatMessage> latest = new ArrayList<>(
                mongoTemplate.find(query, AdminChatMessage.class));
        Collections.reverse(latest);
        return latest.stream().map(AdminChatController::toResponse).toList();
    }

    @PostMapping("/mensajes")
    @ResponseBody
    public MessageResponse sendMessage(
            @RequestParam(name = "texto", required = false) String text,
            @AuthenticationPrincipal AuthenticatedIdentity identity) {
        requireAdminOrModerator(identity);
        String content = text == null ? "" : text.trim();
        if (content.isEmpty() || content.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El mensaje debe tener entre 1 y 1000 caracteres.");
        }

        AdminChatMessage message = new AdminChatMessage();
        message.setAuthorId(identity.id());
        message.setAuthorName(identity.name() == null || identity.name().isBlank()
                ? identity.email() : identity.name());
        message.setAuthorRole(roleLabel(identity.role()));
        message.setText(content);
        message.setCreatedAt(LocalDateTime.now());
        mongoTemplate.save(message);
        return toResponse(message);
    }

    private static void requireAdminOrModerator(AuthenticatedIdentity identity) {
        if (identity == null
                || !("ROLE_ADMIN".equals(identity.role()) || "ROLE_MODERATOR".equals(identity.role()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static String roleLabel(String role) {
        return "ROLE_ADMIN".equals(role) ? "Administrador" : "Moderador";
    }

    private static MessageResponse toResponse(AdminChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getAuthorId(),
                message.getAuthorName(),
                message.getAuthorRole(),
                message.getText(),
                message.getCreatedAt() == null ? "" : message.getCreatedAt().format(DISPLAY_TIME));
    }

    public record MessageResponse(
            String id, String authorId, String authorName, String authorRole, String text, String createdAt) {
    }
}
