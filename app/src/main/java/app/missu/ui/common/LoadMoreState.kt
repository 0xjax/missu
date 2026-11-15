package app.missu.ui.common

/**
 * 分页列表「下一页」的三态。
 *
 * NOTE: 用一个枚举而不是给 `Content` 加两个互不相干的布尔（`loadingMore` / `loadMoreFailed`）——
 * 后者能表达出「正在加载，同时上一次还失败着」这种不存在的组合，界面就得为它想一个行为。
 * NOTE: 「已经到底」不在这里：它由 `nextPage == null` 派生。多存一个 `hasMore` 只是多一处能写错、
 * 且可能与 `nextPage` 打架的地方。
 */
enum class LoadMoreState {
    /** 空闲：滑到底部附近可以自动取下一页。 */
    Idle,

    /** 正在取下一页。 */
    Loading,

    /**
     * 上一次没取到。**停在 Failed 上不再自动触发**——否则用户每滚一下就是一次偷偷的重试，
     * 要显式点重试才继续。
     */
    Failed,
}
