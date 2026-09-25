package io.github.jianfeitu.stackarium.order;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import io.github.jianfeitu.stackarium.experiment.EventReporter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class OrderApplication {
    public static void main(String[] args) { SpringApplication.run(OrderApplication.class, args); }

    @Bean EventReporter eventReporter() { return new EventReporter(); }

    @Bean FlowRule orderLimit() {
        FlowRule rule = new FlowRule("orders-create");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        FlowRuleManager.loadRules(java.util.List.of(rule));
        return rule;
    }
}
