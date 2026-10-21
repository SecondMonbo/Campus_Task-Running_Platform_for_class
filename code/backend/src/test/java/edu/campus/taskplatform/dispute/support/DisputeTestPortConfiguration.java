package edu.campus.taskplatform.dispute.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 用测试替身替换三个集成端口，使取消/申诉模块可在不依赖登录、任务、账务模块的情况下验证。
 */
@TestConfiguration(proxyBeanMethods = false)
public class DisputeTestPortConfiguration {

	@Bean
	MutableCurrentUserPort currentUserPort() {
		return new MutableCurrentUserPort();
	}

	@Bean
	InMemoryTaskPort taskParticipationPort() {
		return new InMemoryTaskPort();
	}

	@Bean
	InMemoryLedgerPort ledgerPort() {
		return new InMemoryLedgerPort();
	}
}
