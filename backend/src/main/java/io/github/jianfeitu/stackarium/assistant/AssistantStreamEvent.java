package io.github.jianfeitu.stackarium.assistant;

public record AssistantStreamEvent(Type type, String text, String tool, ChangeProposal proposal) {
    public enum Type { MESSAGE_DELTA, TOOL_STARTED, TOOL_FINISHED, PROPOSAL_CREATED, DONE, ERROR }

    public static AssistantStreamEvent delta(String text) {
        return new AssistantStreamEvent(Type.MESSAGE_DELTA, text, null, null);
    }

    public static AssistantStreamEvent tool(Type type, String name) {
        return new AssistantStreamEvent(type, null, name, null);
    }

    public static AssistantStreamEvent proposal(ChangeProposal proposal) {
        return new AssistantStreamEvent(Type.PROPOSAL_CREATED, null, null, proposal);
    }

    public static AssistantStreamEvent done() {
        return new AssistantStreamEvent(Type.DONE, null, null, null);
    }

    public static AssistantStreamEvent error(String message) {
        return new AssistantStreamEvent(Type.ERROR, message, null, null);
    }
}
