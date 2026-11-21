package com.painuo.suite.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 内容审核状态机单元测试。
 *
 * <p>状态机是所有"内容型"系统最容易写坏的地方：把流转规则散落在各处 if 里，
 * 半年后没人说得清"驳回的内容还能不能直接发布"。这里把合法流转穷举成用例，
 * 任何新增状态或调整规则都会立刻被测试拦住。</p>
 */
@DisplayName("内容审核状态机")
class ContentStatusTest {

    @Test
    @DisplayName("草稿可以提交审核")
    void draftCanSubmitForReview() {
        assertThat(ContentStatus.DRAFT.canTransitionTo(ContentStatus.PENDING)).isTrue();
        assertThat(ContentStatus.DRAFT.requireTransitionTo(ContentStatus.PENDING)).isEqualTo(ContentStatus.PENDING);
    }

    @Test
    @DisplayName("草稿不能绕过审核直接发布")
    void draftCannotPublishDirectly() {
        assertThat(ContentStatus.DRAFT.canTransitionTo(ContentStatus.PUBLISHED)).isFalse();
        BizException ex = catchThrowableOfType(
                () -> ContentStatus.DRAFT.requireTransitionTo(ContentStatus.PUBLISHED), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.CONTENT_STATE_ILLEGAL);
        assertThat(ex.getMessage()).contains("草稿").contains("已发布");
    }

    @Test
    @DisplayName("待审核可以发布或驳回")
    void pendingCanPublishOrReject() {
        assertThat(ContentStatus.PENDING.canTransitionTo(ContentStatus.PUBLISHED)).isTrue();
        assertThat(ContentStatus.PENDING.canTransitionTo(ContentStatus.REJECTED)).isTrue();
    }

    @Test
    @DisplayName("已发布的内容不能退回草稿，只能下架")
    void publishedCanOnlyGoOffline() {
        assertThat(ContentStatus.PUBLISHED.canTransitionTo(ContentStatus.OFFLINE)).isTrue();
        assertThat(ContentStatus.PUBLISHED.canTransitionTo(ContentStatus.DRAFT)).isFalse();
        assertThat(ContentStatus.PUBLISHED.canTransitionTo(ContentStatus.PENDING)).isFalse();
    }

    @Test
    @DisplayName("已下架可以重新上架，也可以回到待审")
    void offlineCanBeRestored() {
        assertThat(ContentStatus.OFFLINE.canTransitionTo(ContentStatus.PUBLISHED)).isTrue();
        assertThat(ContentStatus.OFFLINE.canTransitionTo(ContentStatus.PENDING)).isTrue();
    }

    @Test
    @DisplayName("被驳回只能重新提交审核")
    void rejectedCanOnlyResubmit() {
        assertThat(ContentStatus.REJECTED.canTransitionTo(ContentStatus.PENDING)).isTrue();
        assertThat(ContentStatus.REJECTED.canTransitionTo(ContentStatus.PUBLISHED)).isFalse();
    }

    @Test
    @DisplayName("状态码与文案互转，未知状态兜底为未知")
    void labelRoundTrip() {
        assertThat(ContentStatus.of(2)).isEqualTo(ContentStatus.PUBLISHED);
        assertThat(ContentStatus.labelOf(2)).isEqualTo("已发布");
        assertThat(ContentStatus.labelOf(null)).isEqualTo("未知");
        assertThat(ContentStatus.labelOf(99)).isEqualTo("未知");
        assertThatThrownBy(() -> ContentStatus.of(99)).isInstanceOf(BizException.class);
    }
}
