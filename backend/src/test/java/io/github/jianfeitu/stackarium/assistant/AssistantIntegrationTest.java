package io.github.jianfeitu.stackarium.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.project.ProjectService;
import io.github.jianfeitu.stackarium.runtime.RuntimeService;
import io.github.jianfeitu.stackarium.runtime.ContainerObservation;
import io.github.jianfeitu.stackarium.runtime.RuntimeStatus;
import io.github.jianfeitu.stackarium.runtime.RuntimeView;
import io.github.jianfeitu.stackarium.topology.RelationType;
import io.github.jianfeitu.stackarium.topology.TopologyEdge;
import io.github.jianfeitu.stackarium.topology.TopologyNode;
import io.github.jianfeitu.stackarium.topology.TopologyService;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Flow;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "STACKARIUM_DB_PASSWORD", matches = ".+")
class AssistantIntegrationTest {
    @Autowired ProjectService projects;
    @Autowired TopologyService topologies;
    @Autowired ArchitectureContextService contexts;
    @Autowired PluginRegistry plugins;
    @Autowired RuntimeService runtimes;
    @Autowired AssistantRepository repository;
    @Autowired ProposalService proposals;
    @Autowired AssistantService assistant;
    @Autowired AssistantModel model;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    UUID projectId;
    UUID inventoryId;
    UUID redisId;
    UUID conversationId;

    @BeforeEach
    void setup() {
        projectId = projects.create("助手集成验证", "").id();
        inventoryId = UUID.randomUUID();
        redisId = UUID.randomUUID();
        topologies.save(projectId, 0, List.of(
                new TopologyNode(inventoryId, "spring-service", "inventory-service", 100, 100,
                        Map.of("serviceName", "inventory-service", "port", "8080")),
                new TopologyNode(redisId, "redis", "Redis", 390, 100, Map.of("port", "6379"))), List.of());
        conversationId = assistant.create(projectId).id();
    }

    @AfterEach
    void cleanup() {
        if (projectId != null) jdbc.update("DELETE FROM projects WHERE id=?", projectId.toString());
    }

    @Test
    void contextAndQueryToolsUseCurrentProjectDataWithoutSecrets() {
        var context = contexts.build(projectId);
        assertThat(context.projectName()).isEqualTo("助手集成验证");
        assertThat(context.revision()).isEqualTo(1);
        assertThat(context.nodes()).hasSize(2);
        assertThat(context.catalog()).anyMatch(item -> item.componentType().equals("redis") && item.runtimeAvailable());
        assertThat(ArchitectureContextService.safeConfig(Map.of("password", "hidden", "port", "6379", "note", "API_KEY=hidden")))
                .doesNotContainKey("password").containsEntry("note", "API_KEY=[已脱敏]");
        ArchitectureTools tools = tools();
        assertThat(tools.getTopology()).contains("inventory-service").contains("\"revision\":1");
        assertThat(tools.getComponentCatalog()).contains("redis").contains("runtimeAvailable");
        assertThat(tools.getRuntimeStatus()).contains("没有 Runtime");
        assertThat(tools.executed()).containsExactly("getTopology", "getComponentCatalog", "getRuntimeStatus");
        assertThat(ArchitectureTools.class.getMethods()).filteredOn(method -> method.isAnnotationPresent(Tool.class))
                .extracting(java.lang.reflect.Method::getName)
                .doesNotContain("shell", "exec", "docker", "bash", "powershell");
    }

    @Test
    void proposalMustBeConfirmedAndSupportsCancelAndRevisionConflict() {
        ChangeProposal connection = proposals.connect(projectId, conversationId, inventoryId, redisId, RelationType.CACHE);
        assertThat(connection.status()).isEqualTo(ChangeProposal.Status.PENDING);
        assertThat(topologies.get(projectId).edges()).isEmpty();
        proposals.cancel(projectId, connection.id());
        assertThat(topologies.get(projectId).edges()).isEmpty();
        assertThatThrownBy(() -> proposals.confirm(projectId, connection.id())).isInstanceOf(ApiException.class);

        ChangeProposal confirmed = proposals.connect(projectId, conversationId, inventoryId, redisId, RelationType.CACHE);
        assertThat(proposals.confirm(projectId, confirmed.id()).revision()).isEqualTo(2);
        assertThat(topologies.get(projectId).edges()).hasSize(1);
        assertThatThrownBy(() -> proposals.connect(projectId, conversationId, inventoryId, redisId, RelationType.CACHE))
                .isInstanceOf(ApiException.class).hasMessageContaining("已经存在");

        ChangeProposal stale = proposals.updateConfig(projectId, conversationId, inventoryId, "port", "8081");
        var current = topologies.get(projectId);
        topologies.save(projectId, current.revision(), current.nodes(), current.edges());
        assertThatThrownBy(() -> proposals.confirm(projectId, stale.id())).isInstanceOf(ApiException.class)
                .hasMessageContaining("重新生成");
    }

    @Test
    void addUpdateRemoveAndInvalidRulesRemainValidated() {
        ChangeProposal added = proposals.add(projectId, conversationId, "redis", "另一个缓存", Map.of(), null, null);
        assertThat(added.operations()).extracting(ChangeOperation::type).containsExactly(ChangeOperation.Type.ADD_NODE);
        assertThat(topologies.get(projectId).nodes()).hasSize(2);
        proposals.confirm(projectId, added.id());
        assertThat(topologies.get(projectId).nodes()).hasSize(3);
        var created = topologies.get(projectId).nodes().stream().filter(node -> node.displayName().equals("另一个缓存"))
                .findFirst().orElseThrow();
        assertThat(created.x()).isNotZero();

        ChangeProposal update = proposals.updateConfig(projectId, conversationId, inventoryId, "port", "8081");
        assertThat(update.operations().get(0).oldValue()).isEqualTo("8080");
        proposals.confirm(projectId, update.id());
        assertThat(topologies.get(projectId).nodes().stream().filter(node -> node.id().equals(inventoryId))
                .findFirst().orElseThrow().config()).containsEntry("port", "8081");

        ChangeProposal removal = proposals.remove(projectId, conversationId, created.id());
        proposals.confirm(projectId, removal.id());
        assertThat(topologies.get(projectId).nodes()).hasSize(2);
        assertThatThrownBy(() -> proposals.add(projectId, conversationId, "unknown", "x", null, null, null))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> proposals.connect(projectId, conversationId, redisId, inventoryId, RelationType.CALL))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> proposals.updateConfig(projectId, conversationId, inventoryId, "port", "70000"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void mutationToolsCreatePendingProposalsWithoutSavingTopology() {
        ArchitectureTools tools = tools();
        String connected = tools.connectComponents(inventoryId.toString(), redisId.toString(), "CACHE");
        assertThat(connected).contains("待确认");
        assertThat(topologies.get(projectId).edges()).isEmpty();
        assertThat(tools.createdProposals()).hasSize(1);
        assertThat(repository.proposal(projectId, tools.createdProposals().get(0)).status())
                .isEqualTo(ChangeProposal.Status.PENDING);
        assertThat(tools.updateComponentConfig(inventoryId.toString(), "port", "8081")).contains("待确认");
        assertThat(topologies.get(projectId).nodes().stream().filter(node -> node.id().equals(inventoryId))
                .findFirst().orElseThrow().config()).containsEntry("port", "8080");
        assertThat(tools.addComponent("redis", "another-redis", Map.of(), null, null)).contains("待确认");
        assertThat(topologies.get(projectId).nodes()).hasSize(2);
    }

    @Test
    void runtimeToolsCallTheExistingRuntimeService() {
        RuntimeService actualServiceBoundary = mock(RuntimeService.class);
        UUID runtimeId = UUID.randomUUID();
        Instant now = Instant.now();
        RuntimeView observed = new RuntimeView(runtimeId, projectId, 1, RuntimeStatus.RUNNING,
                null, null, null, now, now, null, now,
                List.of(new ContainerObservation(inventoryId, "spring-service", "inventory-service",
                        "inventory-service", "container", "running", "healthy", now)));
        when(actualServiceBoundary.latest(projectId)).thenReturn(Optional.of(observed));
        when(actualServiceBoundary.generate(projectId)).thenReturn(observed);
        ArchitectureTools tools = new ArchitectureTools(projectId, conversationId, contexts, plugins,
                actualServiceBoundary, proposals, json);
        assertThat(tools.getRuntimeStatus()).contains("RUNNING").contains("healthy");
        assertThat(tools.generateRuntimeConfig()).contains(runtimeId.toString());
        verify(actualServiceBoundary).latest(projectId);
        verify(actualServiceBoundary).generate(projectId);
    }

    @Test
    void streamingCallsToolCreatesProposalAndPersistsOnlyFinalAnswer() {
        AtomicInteger calls = new AtomicInteger();
        StreamingChatModel deterministicModel = new StreamingChatModel() {
            @Override public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
                assertThat(request.toolSpecifications()).extracting(spec -> spec.name())
                        .contains("getTopology", "getRuntimeStatus", "connectComponents")
                        .doesNotContain("shell", "exec", "dockerPs");
                if (calls.getAndIncrement() == 0) {
                    var toolCall = ToolExecutionRequest.builder().id("tool-1").name("connectComponents")
                            .arguments("{\"sourceNodeId\":\"" + inventoryId + "\",\"targetNodeId\":\""
                                    + redisId + "\",\"relationType\":\"CACHE\"}").build();
                    return events(List.of(new CompleteResponse(
                            ChatResponse.builder().aiMessage(AiMessage.from(toolCall)).build())));
                }
                return events(List.of(new PartialResponse("建议已创建，"),
                        new PartialResponse("等待确认。"),
                        new CompleteResponse(ChatResponse.builder()
                                .aiMessage(AiMessage.from("建议已创建，等待确认。")).build())));
            }
        };
        AssistantModel configuredModel = mock(AssistantModel.class);
        when(configuredModel.configured()).thenReturn(true);
        when(configuredModel.get()).thenReturn(deterministicModel);
        AssistantService service = new AssistantService(projects, repository, contexts, plugins, runtimes,
                proposals, configuredModel, json);

        var emitted = service.stream(projectId, conversationId, "连接库存服务和 Redis")
                .collectList().block(Duration.ofSeconds(5));
        assertThat(emitted).isNotNull();
        assertThat(emitted.stream().filter(event -> event.type() == AssistantStreamEvent.Type.MESSAGE_DELTA)
                .map(AssistantStreamEvent::text)).containsExactly("建议已创建，", "等待确认。");
        assertThat(emitted.stream().map(AssistantStreamEvent::type))
                .contains(AssistantStreamEvent.Type.TOOL_STARTED, AssistantStreamEvent.Type.TOOL_FINISHED,
                        AssistantStreamEvent.Type.PROPOSAL_CREATED, AssistantStreamEvent.Type.DONE);
        assertThat(repository.messages(conversationId)).extracting(AssistantRepository.Message::role)
                .containsExactly("user", "assistant");
        assertThat(repository.messages(conversationId).get(1).content()).isEqualTo("建议已创建，等待确认。");
        assertThat(topologies.get(projectId).edges()).isEmpty();
        assertThat(repository.proposals(projectId, conversationId)).hasSize(1);
    }

    private Flow.Publisher<ChatModelStreamingEvent> events(List<ChatModelStreamingEvent> values) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private int index;
            private boolean closed;
            @Override public void request(long count) {
                while (count-- > 0 && !closed && index < values.size()) {
                    ChatModelStreamingEvent event = values.get(index++);
                    if (event instanceof PartialResponse)
                        assertThat(repository.messages(conversationId)).extracting(AssistantRepository.Message::role)
                                .containsExactly("user");
                    subscriber.onNext(event);
                }
                if (!closed && index == values.size()) {
                    closed = true;
                    subscriber.onComplete();
                }
            }
            @Override public void cancel() { closed = true; }
        });
    }
    @Test
    void streamingErrorKeepsUserMessageWithoutAssistantHistory() {
        StreamingChatModel failingModel = new StreamingChatModel() {
            @Override public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
                return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    private boolean sent;
                    @Override public void request(long count) {
                        if (!sent) {
                            sent = true;
                            subscriber.onNext(new PartialResponse("部分中文"));
                            subscriber.onError(new dev.langchain4j.exception.HttpException(401, "unauthorized"));
                        }
                    }
                    @Override public void cancel() { sent = true; }
                });
            }
        };
        AssistantModel configuredModel = mock(AssistantModel.class);
        when(configuredModel.configured()).thenReturn(true);
        when(configuredModel.get()).thenReturn(failingModel);
        AssistantService service = new AssistantService(projects, repository, contexts, plugins, runtimes,
                proposals, configuredModel, json);

        var emitted = service.stream(projectId, conversationId, "解释当前架构")
                .collectList().block(Duration.ofSeconds(5));
        assertThat(emitted).isNotNull();
        assertThat(emitted.stream().map(AssistantStreamEvent::type))
                .containsExactly(AssistantStreamEvent.Type.MESSAGE_DELTA, AssistantStreamEvent.Type.ERROR);
        assertThat(emitted.get(1).text()).contains("API_KEY");
        assertThat(repository.messages(conversationId)).extracting(AssistantRepository.Message::role)
                .containsExactly("user");
    }
    @Test
    void cancellingFluxCancelsModelSubscriptionWithoutSavingAssistantMessage() {
        AtomicBoolean cancelled = new AtomicBoolean();
        StreamingChatModel slowModel = new StreamingChatModel() {
            @Override public Flow.Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
                return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
                    private boolean emitted;
                    @Override public void request(long count) {
                        if (!emitted) {
                            emitted = true;
                            subscriber.onNext(new PartialResponse("部分内容"));
                        }
                    }
                    @Override public void cancel() { cancelled.set(true); }
                });
            }
        };
        AssistantModel configuredModel = mock(AssistantModel.class);
        when(configuredModel.configured()).thenReturn(true);
        when(configuredModel.get()).thenReturn(slowModel);
        AssistantService service = new AssistantService(projects, repository, contexts, plugins, runtimes,
                proposals, configuredModel, json);
        var emitted = service.stream(projectId, conversationId, "解释当前架构")
                .take(1).collectList().block(Duration.ofSeconds(5));
        assertThat(emitted).isNotNull();
        assertThat(emitted).extracting(AssistantStreamEvent::text).containsExactly("部分内容");
        assertThat(cancelled).isTrue();
        assertThat(repository.messages(conversationId)).extracting(AssistantRepository.Message::role)
                .containsExactly("user");
    }
    @Test
    void conversationAndProposalAreProjectScopedAndHistoryRecovers() {
        repository.addMessage(conversationId, "user", "请使用 API_KEY=secret-value");
        repository.addMessage(conversationId, "assistant", "收到");
        var recovered = assistant.get(projectId, conversationId);
        assertThat(recovered.messages()).hasSize(2);
        assertThat(recovered.messages().get(0).content()).doesNotContain("secret-value");
        UUID other = projects.create("另一个项目", "").id();
        try {
            assertThatThrownBy(() -> assistant.get(other, conversationId)).isInstanceOf(ApiException.class);
            var proposal = proposals.connect(projectId, conversationId, inventoryId, redisId, RelationType.CACHE);
            assertThatThrownBy(() -> proposals.confirm(other, proposal.id())).isInstanceOf(ApiException.class);
        } finally {
            jdbc.update("DELETE FROM projects WHERE id=?", other.toString());
        }
    }

    @Test
    void emptyConversationsDoNotClutterHistory() {
        UUID anotherEmpty = assistant.create(projectId).id();
        assertThat(assistant.list(projectId)).isEmpty();
        repository.addMessage(conversationId, "user", "解释当前架构");
        assertThat(assistant.list(projectId)).extracting(AssistantRepository.Conversation::id)
                .containsExactly(conversationId).doesNotContain(anotherEmpty);
    }

    @Test
    void unconfiguredModelDoesNotBlockCanvasServices() {
        if (!model.configured()) {
            assertThatThrownBy(() -> assistant.stream(projectId, conversationId, "解释当前架构"))
                    .isInstanceOf(ApiException.class).hasMessageContaining("尚未配置");
        }
        assertThat(topologies.get(projectId).nodes()).hasSize(2);
    }

    private ArchitectureTools tools() {
        return new ArchitectureTools(projectId, conversationId, contexts, plugins, runtimes, proposals, json);
    }
}
