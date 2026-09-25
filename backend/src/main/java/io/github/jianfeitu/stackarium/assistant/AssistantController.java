package io.github.jianfeitu.stackarium.assistant;

import io.github.jianfeitu.stackarium.topology.Topology;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import reactor.core.publisher.Flux;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assistant")
public class AssistantController {
    public record SendRequest(@NotBlank String message) {}
    private final AssistantService assistant;
    private final ProposalService proposals;

    public AssistantController(AssistantService assistant, ProposalService proposals) {
        this.assistant = assistant;
        this.proposals = proposals;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status(@PathVariable UUID projectId) {
        return Map.of("modelConfigured", assistant.configured(projectId));
    }

    @GetMapping("/conversations")
    public List<AssistantRepository.Conversation> list(@PathVariable UUID projectId) {
        return assistant.list(projectId);
    }

    @PostMapping("/conversations")
    public AssistantRepository.Conversation create(@PathVariable UUID projectId) {
        return assistant.create(projectId);
    }

    @GetMapping("/conversations/{conversationId}")
    public AssistantService.ConversationView conversation(@PathVariable UUID projectId,
                                                          @PathVariable UUID conversationId) {
        return assistant.get(projectId, conversationId);
    }

    @PostMapping(value = "/conversations/{conversationId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<AssistantStreamEvent> send(@PathVariable UUID projectId, @PathVariable UUID conversationId,
                                       @Valid @RequestBody SendRequest request) {
        return assistant.stream(projectId, conversationId, request.message());
    }

    @PostMapping("/proposals/{proposalId}/confirm")
    public Topology confirm(@PathVariable UUID projectId, @PathVariable UUID proposalId) {
        return proposals.confirm(projectId, proposalId);
    }

    @PostMapping("/proposals/{proposalId}/cancel")
    public ChangeProposal cancel(@PathVariable UUID projectId, @PathVariable UUID proposalId) {
        return proposals.cancel(projectId, proposalId);
    }
}
