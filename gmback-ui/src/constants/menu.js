/** 侧栏菜单顺序、路由 path、权限 key、页面标题 */
export const MENU_ITEMS = [
    {key: 'monitor', path: '/monitor', title: '节点监控'},
    {key: 'server_status', path: '/server-status', title: '服务器关闭'},
    {key: 'online_player', path: '/online-player', title: '在线玩家'},
    {key: 'config_update', path: '/config-update', title: '配置更新'},
    {key: 'hotswap_jar', path: '/hotswap-jar', title: '代码热更'},
    {key: 'send_mail', path: '/send-mail', title: '发送邮件'},
    {key: 'kick_human', path: '/kick-human', title: '玩家下线'},
    {key: 'ban_player', path: '/ban-player', title: '玩家封禁'},
    {key: 'mute_player', path: '/mute-player', title: '玩家禁言'},
    {key: 'whitelist', path: '/whitelist', title: '白名单'},
    {key: 'announcement', path: '/announcement', title: '全服公告'},
    {key: 'cdk', path: '/cdk', title: '兑换码'},
    {key: 'operation_log', path: '/operation-log', title: '操作记录'},
    {key: 'user_manager', path: '/user-manager', title: '用户管理', adminOnly: true},
];

export const ROUTE_TITLES = Object.fromEntries(
    MENU_ITEMS.map((item) => [item.key, item.title])
);

export function pageKeyToComponentName(key) {
    return key
        .split('_')
        .map((s) => s.charAt(0).toUpperCase() + s.slice(1))
        .join('');
}

export function getFirstAllowedRoute(hasPermission, isAdmin) {
    for (const item of MENU_ITEMS) {
        if (item.adminOnly) {
            if (isAdmin) return item.path;
            continue;
        }
        if (hasPermission(item.key)) return item.path;
    }
    return null;
}
