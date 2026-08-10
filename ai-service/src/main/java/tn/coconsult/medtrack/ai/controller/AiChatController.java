package tn.coconsult.medtrack.ai.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.coconsult.medtrack.ai.dto.ChatRequest;
import tn.coconsult.medtrack.ai.dto.ChatResponse;
import tn.coconsult.medtrack.ai.service.AiChatService;

@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatService aiChatService;

    @PostMapping("/{patientId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MEDECIN')")
    public ResponseEntity<ChatResponse> chat(@PathVariable Long patientId, @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(aiChatService.chat(patientId, request.message(), request.sessionId()));
    }
}
