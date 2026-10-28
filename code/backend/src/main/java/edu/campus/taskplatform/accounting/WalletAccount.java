package edu.campus.taskplatform.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "wallet_account")
public class WalletAccount {

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "available_balance", nullable = false)
	private long availableBalance;

	@Column(name = "frozen_balance", nullable = false)
	private long frozenBalance;

	protected WalletAccount() {
	}

	public WalletAccount(Long userId, long availableBalance, long frozenBalance) {
		this.userId = userId;
		this.availableBalance = availableBalance;
		this.frozenBalance = frozenBalance;
	}

	public Long getUserId() {
		return userId;
	}

	public long getAvailableBalance() {
		return availableBalance;
	}

	public long getFrozenBalance() {
		return frozenBalance;
	}

	void setAvailableBalance(long availableBalance) {
		this.availableBalance = availableBalance;
	}

	void setFrozenBalance(long frozenBalance) {
		this.frozenBalance = frozenBalance;
	}
}
