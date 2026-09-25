package io.github.jianfeitu.stackarium.assistant;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import io.github.jianfeitu.stackarium.common.ApiException;
import io.github.jianfeitu.stackarium.plugin.PluginRegistry;
import io.github.jianfeitu.stackarium.project.ProjectService;
import io.github.jianfeitu.stackarium.runtime.RuntimeService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import tools.jackson.databind.ObjectMapper;

@Service
public class AssistantService {
    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    interface Agent {
        @SystemMessage("你是 Stackarium 架构助手，只讨论当前项目。架构事实以当前上下文和工具为准；实时状态必须调用 getRuntimeStatus。不了解就说明不知道，不得编造 Docker 或中间件状态。不得读取密钥或执行系统命令。架构修改只能调用受控工具生成建议，必须由用户确认；工具仍受平台校验。回答使用简洁中文和短段落，不用 Markdown 表格。")
        Flow.Publisher<AiServiceStreamingEvent> chat(String message);
    }

    public record ConversationView(AssistantRepository.Conversation conversation,
                                   List<AssistantRepository.Message> messages,
                                   List<ChangeProposal> proposals, boolean modelConfigured) {}

    private final ProjectService projects;
    private final AssistantRepository repository;
    private final ArchitectureContextService contexts;
    private final PluginRegistry plugins;
    private final RuntimeService runtimes;
    private final ProposalService proposals;
    private final AssistantModel model;
    private final ObjectMapper json;

    public AssistantService(ProjectService projects, AssistantRepository repository, ArchitectureContextService contexts,
                            PluginRegistry plugins, RuntimeService runtimes, ProposalService proposals,
                            AssistantModel model, ObjectMapper json) {
        this.projects = projects;
        this.repository = repository;
        this.contexts = contexts;
        this.plugins = plugins;
        this.runtimes = runtimes;
        this.proposals = proposals;
        this.model = model;
        this.json = json;
    }

    public boolean configured(UUID projectId) { projects.get(projectId); return model.configured(); }

    public AssistantRepository.Conversation create(UUID projectId) {
        projects.get(projectId);
        return repository.createConversation(projectId);
    }

    public List<AssistantRepository.Conversation> list(UUID projectId) {
        projects.get(projectId);
        return repository.conversations(projectId);
    }

    public ConversationView get(UUID projectId, UUID conversationId) {
        var conversation = repository.conversation(projectId, conversationId);
        return new ConversationView(conversation, repository.messages(conversationId),
                repository.proposals(projectId, conversationId), model.configured());
    }

    public Flux<AssistantStreamEvent> stream(UUID projectId, UUID conversationId, String rawMessage) {
        repository.conversation(projectId, conversationId);
        if (rawMessage == null || rawMessage.isBlank() || rawMessage.length() > 2000)
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MESSAGE", "请输入不超过 2000 字的消息");
        if (!model.configured()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI_NOT_CONFIGURED", "AI 模型尚未配置");

        String message = Secrets.redact(rawMessage.trim());
        List<AssistantRepository.Message> recent = repository.recentMessages(conversationId);
        StringBuilder prompt = new StringBuilder("当前 ArchitectureContext（平台实时构造，敏感配置已过滤）：\n")
                .append(json.writeValueAsString(contexts.build(projectId))).append("\n\n最近会话：\n");
        for (var prior : recent) prompt.append(prior.role()).append("：").append(prior.content()).append("\n");
        prompt.append("\n当前用户请求：").append(message);

        ArchitectureTools tools = new ArchitectureTools(projectId, conversationId, contexts, plugins, runtimes, proposals, json);
        Agent agent = AiServices.builder(Agent.class).streamingChatModel(model.get()).tools(tools).build();
        repository.addMessage(conversationId, "user", message);
        Flow.Publisher<AiServiceStreamingEvent> publisher = agent.chat(prompt.toString());
        return Flux.create(sink -> subscribe(projectId, conversationId, tools, publisher, sink), FluxSink.OverflowStrategy.BUFFER);
    }

    private void subscribe(UUID projectId, UUID conversationId, ArchitectureTools tools,
                           Flow.Publisher<AiServiceStreamingEvent> publisher, FluxSink<AssistantStreamEvent> sink) {
        AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
        sink.onCancel(() -> {
            Flow.Subscription active = subscription.get();
            if (active != null) active.cancel();
        });
        publisher.subscribe(new Flow.Subscriber<>() {
            private final StringBuilder answer = new StringBuilder();
            private int emittedProposals;
            private boolean finished;

            @Override public void onSubscribe(Flow.Subscription active) {
                subscription.set(active);
                if (sink.isCancelled()) active.cancel();
                else active.request(1);
            }

            @Override public void onNext(AiServiceStreamingEvent event) {
                if (finished || sink.isCancelled()) return;
                try {
                    if (event instanceof AiServiceStreamingEvent.PartialResponseEvent partial) {
                        String text = partial.partialResponse().text();
                        if (text != null && !text.isEmpty()) {
                            answer.append(text);
                            sink.next(AssistantStreamEvent.delta(text));
                        }
                    } else if (event instanceof AiServiceStreamingEvent.BeforeToolExecutionEvent before) {
                        sink.next(AssistantStreamEvent.tool(AssistantStreamEvent.Type.TOOL_STARTED,
                                before.beforeToolExecution().request().name()));
                    } else if (event instanceof AiServiceStreamingEvent.AfterToolExecutionEvent after) {
                        sink.next(AssistantStreamEvent.tool(AssistantStreamEvent.Type.TOOL_FINISHED,
                                after.toolExecution().request().name()));
                        List<UUID> created = tools.createdProposals();
                        while (emittedProposals < created.size())
                            sink.next(AssistantStreamEvent.proposal(repository.proposal(projectId,
                                    created.get(emittedProposals++))));
                    } else if (event instanceof AiServiceStreamingEvent.FinalResponseEvent finalEvent) {
                        String finalText = finalEvent.chatResponse().aiMessage().text();
                        if (answer.isEmpty() && finalText != null && !finalText.isEmpty()) {
                            answer.append(finalText);
                            sink.next(AssistantStreamEvent.delta(finalText));
                        }
                        if (!answer.isEmpty())
                            repository.addMessage(conversationId, "assistant", Secrets.redact(answer.toString()));
                        finished = true;
                        sink.next(AssistantStreamEvent.done());
                        sink.complete();
                    }
                    if (!finished && !sink.isCancelled()) subscription.get().request(1);
                } catch (RuntimeException error) {
                    fail(error);
                }
            }

            @Override public void onError(Throwable error) { fail(error); }

            @Override public void onComplete() {
                if (!finished && !sink.isCancelled()) fail(new IllegalStateException("模型响应未完成"));
            }

            private void fail(Throwable error) {
                if (finished || sink.isCancelled()) return;
                finished = true;
                sink.next(AssistantStreamEvent.error(friendlyError(error).getMessage()));
                sink.complete();
                Flow.Subscription active = subscription.get();
                if (active != null) active.cancel();
            }
        });
    }

    private ApiException friendlyError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        log.warn("AI model stream failed: {} -> {}", error.getClass().getSimpleName(),
                cause.getClass().getSimpleName());
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof AuthenticationException)
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_AUTH_FAILED",
                        "模型认证失败，请检查 API_KEY");
            if (current instanceof HttpException http) {
                log.warn("AI provider HTTP status: {}", http.statusCode());
                if (http.statusCode() == 401 || http.statusCode() == 403)
                    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_AUTH_FAILED",
                            "模型认证失败，请检查 API_KEY");
                if (http.statusCode() == 404)
                    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_MODEL_NOT_FOUND",
                            "模型或接口地址无效，请检查 MODEL 和 BASE_URL");
                if (http.statusCode() == 400 || http.statusCode() == 422)
                    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_REQUEST_REJECTED",
                            "模型接口拒绝了工具调用，请检查模型的 Tool Calling 兼容性");
                if (http.statusCode() == 429)
                    return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_RATE_LIMIT",
                            "模型服务请求过于频繁，请稍后重试");
            }
        }
        String kind = error.getClass().getSimpleName().toLowerCase();
        String detail = String.valueOf(error.getMessage()).toLowerCase();
        if (kind.contains("timeout") || detail.contains("timed out"))
            return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "请求超时，请稍后重试");
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "模型服务暂时不可用");
    }
}
