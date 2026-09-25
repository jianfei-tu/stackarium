package io.github.jianfeitu.stackarium.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OrderLimitTest {
    @AfterEach void clearRules() { FlowRuleManager.loadRules(java.util.List.of()); }

    @Test
    void createOrderResourceHasARealSentinelQpsRule() throws Exception {
        var rule = new OrderApplication().orderLimit();
        assertThat(rule.getResource()).isEqualTo("orders-create");
        assertThat(rule.getCount()).isEqualTo(1);
        try (Entry ignored = SphU.entry("orders-create")) {
            assertThatThrownBy(() -> SphU.entry("orders-create"))
                    .isInstanceOf(BlockException.class);
        }
    }
}
