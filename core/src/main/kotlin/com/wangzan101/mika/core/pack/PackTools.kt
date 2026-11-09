package com.wangzan101.mika.core.pack

/** 包校验用的现有工具名：提示词 `prompts/tools/` 下的文件名。 */
object PackTools {
    val NAMES = setOf(
        "ask_user", "clear_text", "describe", "diagnose", "do_steps", "end_not_done", "find_visually",
        "hand_to_user", "handle_obstacle", "history_read", "knowledge_read", "knowledge_search",
        "list_elements", "memory_edit", "memory_save", "memory_search",
        "phone_add_calendar_event", "phone_app_after_task", "phone_back", "phone_clear_text",
        "phone_collect", "phone_dialog", "phone_event", "phone_extract", "phone_find", "phone_gesture",
        "phone_launch_app", "phone_list_apps", "phone_list_elements", "phone_observe", "phone_open_link",
        "phone_press_key", "phone_query", "phone_run", "phone_scroll", "phone_set_alarm", "phone_set_timer",
        "phone_set_wheel", "phone_share_text", "phone_show_alarms", "phone_swipe", "phone_system_command",
        "phone_tap", "phone_type_text", "phone_wait",
        "press_key", "profile_edit", "read_calendar", "read_file", "read_next_alarm", "read_notifications", "request_confirmation",
        "root_run_command", "run_bundled_script", "run_command", "schedule_task", "screenshot", "scroll",
        "search_files", "settings_list", "settings_set", "share_file", "skill_read", "skill_save",
        "skill_search", "spawn_agent", "swipe", "tap", "type_text", "view_past_screens", "view_user_screen",
        "wait", "web_back", "web_capture", "web_downloads", "web_evaluate_js", "web_interact", "web_keep_open",
        "web_list_elements", "web_navigate", "web_observe", "web_read_page", "web_run_site_command",
        "web_script", "web_search", "web_site_mode", "web_tabs", "web_upload", "web_wait_for", "write_file",
    )
}
