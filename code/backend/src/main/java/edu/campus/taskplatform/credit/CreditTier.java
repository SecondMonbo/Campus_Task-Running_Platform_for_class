package edu.campus.taskplatform.credit;

/**
 * 信用分档位：90–100 优秀、75–89 良好、60–74 预警、0–59 受限。
 * 边界口径与 API约定 §4.4 一致：恰为 60 分不限制，恰为 75 分视为已解除限制。
 */
public enum CreditTier {

    EXCELLENT("优秀", 90, 100),
    GOOD("良好", 75, 89),
    WARNING("预警", 60, 74),
    RESTRICTED("受限", 0, 59);

    private final String label;
    private final int min;
    private final int max;

    CreditTier(String label, int min, int max) {
        this.label = label;
        this.min = min;
        this.max = max;
    }

    public String label() {
        return label;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public static CreditTier fromScore(int score) {
        if (score >= EXCELLENT.min) {
            return EXCELLENT;
        }
        if (score >= GOOD.min) {
            return GOOD;
        }
        if (score >= WARNING.min) {
            return WARNING;
        }
        return RESTRICTED;
    }

    /** 本人可见的当前权限说明。 */
    public String permissionHint() {
        return switch (this) {
            case EXCELLENT -> "信用优秀，可正常发布与接取任务。";
            case GOOD -> "信用良好，可正常发布与接取任务。";
            case WARNING -> "信用预警，仍可发布与接取任务，请留意履约风险。";
            case RESTRICTED -> "信用受限，暂不可接取普通任务；新发布任务需管理员复核后公开。";
        };
    }

    /** 恢复条件说明。 */
    public String recoveryHint() {
        return switch (this) {
            case EXCELLENT, GOOD -> "保持按时完成与良好评价即可维持当前档位。";
            case WARNING -> "连续完成 10 个任务且期间无负向信用事件后，可恢复至 75 分。";
            case RESTRICTED -> "连续完成 10 个任务且期间无负向信用事件后，分值恢复至 75，并解除接单限制。";
        };
    }
}
