/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

#include <speech-dispatcher/libspeechd.h>
#include <gio/gio.h>
#include <json-glib/json-glib.h>
#include <math.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>

static GMainLoop *loop;
static SPDConnection *speech;
static GDBusConnection *bus;
static GDBusNodeInfo *node;
static guint bus_owner, root_registration, player_registration;
static gint active_message = -1;
static gchar *active_id;
static gchar *pause_request;
static gint pause_message = -1;
static gchar *title, *author, *album;
static gboolean active_session, playing, can_previous, can_next;
static double rate = 1.0;
static guint generation;
static const gchar *path = "/org/mpris/MediaPlayer2";

static void emit(const gchar *event, const gchar *id, const gchar *key, const gchar *value) {
    JsonBuilder *builder = json_builder_new();
    json_builder_begin_object(builder);
    json_builder_set_member_name(builder, "event"); json_builder_add_string_value(builder, event);
    if (id) { json_builder_set_member_name(builder, "id"); json_builder_add_string_value(builder, id); }
    if (key) { json_builder_set_member_name(builder, key); json_builder_add_string_value(builder, value ? value : ""); }
    json_builder_end_object(builder);
    JsonNode *root = json_builder_get_root(builder);
    JsonGenerator *generator = json_generator_new();
    json_generator_set_root(generator, root);
    gchar *line = json_generator_to_data(generator, NULL);
    fputs(line, stdout); fputc('\n', stdout); fflush(stdout);
    g_free(line); json_node_free(root); g_object_unref(generator); g_object_unref(builder);
}

static void acknowledge_pause(void) {
    if (pause_request) {
        emit("ack", NULL, "request", pause_request);
        g_clear_pointer(&pause_request, g_free);
        pause_message = -1;
    }
}

static void clear_utterance(void) {
    active_message = -1;
    g_clear_pointer(&active_id, g_free);
    if (speech) spd_cancel(speech);
}

struct SpeechEvent { size_t message; SPDNotificationType type; guint generation; };
static gboolean deliver_speech(gpointer data) {
    struct SpeechEvent *event = data;
    if (event->generation == generation && (gint)event->message == pause_message &&
        (event->type == SPD_EVENT_PAUSE || event->type == SPD_EVENT_END || event->type == SPD_EVENT_CANCEL)) acknowledge_pause();
    if (event->generation == generation && (gint)event->message == active_message && active_id) {
        if (event->type == SPD_EVENT_BEGIN || event->type == SPD_EVENT_RESUME) {
            emit("start", active_id, NULL, NULL);
        } else if (event->type == SPD_EVENT_PAUSE) {
            acknowledge_pause();
        } else if (event->type == SPD_EVENT_END) {
            acknowledge_pause();
            emit("end", active_id, NULL, NULL);
            active_message = -1;
            g_clear_pointer(&active_id, g_free);
        } else if (event->type == SPD_EVENT_CANCEL) {
            emit("error", active_id, "message", "Speech was cancelled by the system");
            active_message = -1;
            g_clear_pointer(&active_id, g_free);
        }
    }
    g_free(event);
    return G_SOURCE_REMOVE;
}

// libspeechd invokes notifications on its worker. Only the GLib main context owns state.
static void speech_event(size_t message, size_t client, SPDNotificationType type) {
    (void)client;
    struct SpeechEvent *event = g_new(struct SpeechEvent, 1);
    event->message = message;
    event->type = type;
    event->generation = (guint)g_atomic_int_get((gint *)&generation);
    g_main_context_invoke(NULL, deliver_speech, event);
}

static GVariant *metadata(void) {
    GVariantBuilder result;
    g_variant_builder_init(&result, G_VARIANT_TYPE("a{sv}"));
    if (active_session) {
        g_variant_builder_add(&result, "{sv}", "mpris:trackid", g_variant_new_object_path("/org/mpris/MediaPlayer2/reading"));
        g_variant_builder_add(&result, "{sv}", "xesam:title", g_variant_new_string(title ? title : ""));
        const gchar *artists[] = {author ? author : "", NULL};
        g_variant_builder_add(&result, "{sv}", "xesam:artist", g_variant_new_strv(artists, -1));
        g_variant_builder_add(&result, "{sv}", "xesam:album", g_variant_new_string(album ? album : ""));
    }
    return g_variant_builder_end(&result);
}

static const gchar *status(void) { return !active_session ? "Stopped" : playing ? "Playing" : "Paused"; }
static void publish_mpris(void) {
    if (!bus) return;
    GVariantBuilder changed;
    g_variant_builder_init(&changed, G_VARIANT_TYPE("a{sv}"));
    g_variant_builder_add(&changed, "{sv}", "PlaybackStatus", g_variant_new_string(status()));
    g_variant_builder_add(&changed, "{sv}", "Metadata", metadata());
    g_variant_builder_add(&changed, "{sv}", "CanGoPrevious", g_variant_new_boolean(active_session && can_previous));
    g_variant_builder_add(&changed, "{sv}", "CanGoNext", g_variant_new_boolean(active_session && can_next));
    g_variant_builder_add(&changed, "{sv}", "CanPlay", g_variant_new_boolean(active_session));
    g_variant_builder_add(&changed, "{sv}", "CanPause", g_variant_new_boolean(active_session));
    g_dbus_connection_emit_signal(bus, NULL, path, "org.freedesktop.DBus.Properties", "PropertiesChanged",
        g_variant_new("(sa{sv}as)", "org.mpris.MediaPlayer2.Player", &changed, NULL), NULL);
}

static void method_call(GDBusConnection *connection, const gchar *sender, const gchar *object_path,
    const gchar *interface, const gchar *method, GVariant *parameters, GDBusMethodInvocation *invocation, gpointer data) {
    (void)connection; (void)sender; (void)object_path; (void)parameters; (void)data;
    const gchar *action = NULL;
    if (!strcmp(interface, "org.mpris.MediaPlayer2.Player") && active_session) {
        if (!strcmp(method, "Play")) action = "Play";
        else if (!strcmp(method, "Pause")) action = "Pause";
        else if (!strcmp(method, "PlayPause")) action = playing ? "Pause" : "Play";
        else if (!strcmp(method, "Stop")) action = "Stop";
        else if (!strcmp(method, "Next") && can_next) action = "Next";
        else if (!strcmp(method, "Previous") && can_previous) action = "Previous";
    }
    if (action) {
        emit("command", NULL, "action", action);
        g_dbus_method_invocation_return_value(invocation, NULL);
    } else g_dbus_method_invocation_return_error(invocation, G_DBUS_ERROR, G_DBUS_ERROR_NOT_SUPPORTED, "Reading command is unavailable");
}

static GVariant *get_property(GDBusConnection *connection, const gchar *sender, const gchar *object_path,
    const gchar *interface, const gchar *property, GError **error, gpointer data) {
    (void)connection; (void)sender; (void)object_path; (void)data;
    if (!strcmp(interface, "org.mpris.MediaPlayer2")) {
        if (!strcmp(property, "Identity")) return g_variant_new_string("Zhihu++");
        if (!strcmp(property, "CanQuit") || !strcmp(property, "CanRaise") || !strcmp(property, "HasTrackList")) return g_variant_new_boolean(FALSE);
        if (!strcmp(property, "SupportedUriSchemes") || !strcmp(property, "SupportedMimeTypes")) return g_variant_new_strv(NULL, 0);
    } else {
        if (!strcmp(property, "PlaybackStatus")) return g_variant_new_string(status());
        if (!strcmp(property, "Metadata")) return metadata();
        if (!strcmp(property, "Rate") || !strcmp(property, "Volume")) return g_variant_new_double(!strcmp(property, "Rate") ? rate : 1.0);
        if (!strcmp(property, "MinimumRate")) return g_variant_new_double(0.5);
        if (!strcmp(property, "MaximumRate")) return g_variant_new_double(2.0);
        if (!strcmp(property, "Position")) return g_variant_new_int64(0);
        if (!strcmp(property, "CanSeek")) return g_variant_new_boolean(FALSE);
        if (!strcmp(property, "CanControl")) return g_variant_new_boolean(TRUE);
        if (!strcmp(property, "CanPlay") || !strcmp(property, "CanPause")) return g_variant_new_boolean(active_session);
        if (!strcmp(property, "CanGoNext")) return g_variant_new_boolean(active_session && can_next);
        if (!strcmp(property, "CanGoPrevious")) return g_variant_new_boolean(active_session && can_previous);
    }
    g_set_error(error, G_DBUS_ERROR, G_DBUS_ERROR_UNKNOWN_PROPERTY, "Unknown reading property");
    return NULL;
}

static const GDBusInterfaceVTable vtable = {method_call, get_property, NULL, {0}};
static const gchar *introspection =
    "<node><interface name='org.mpris.MediaPlayer2'>"
    "<method name='Raise'/><method name='Quit'/>"
    "<property name='CanQuit' type='b' access='read'/><property name='CanRaise' type='b' access='read'/>"
    "<property name='HasTrackList' type='b' access='read'/><property name='Identity' type='s' access='read'/>"
    "<property name='SupportedUriSchemes' type='as' access='read'/><property name='SupportedMimeTypes' type='as' access='read'/>"
    "</interface><interface name='org.mpris.MediaPlayer2.Player'>"
    "<method name='Play'/><method name='Pause'/><method name='PlayPause'/><method name='Stop'/><method name='Next'/><method name='Previous'/>"
    "<property name='PlaybackStatus' type='s' access='read'/><property name='Metadata' type='a{sv}' access='read'/>"
    "<property name='Rate' type='d' access='read'/><property name='Volume' type='d' access='read'/>"
    "<property name='MinimumRate' type='d' access='read'/><property name='MaximumRate' type='d' access='read'/>"
    "<property name='Position' type='x' access='read'/><property name='CanSeek' type='b' access='read'/>"
    "<property name='CanControl' type='b' access='read'/><property name='CanPlay' type='b' access='read'/><property name='CanPause' type='b' access='read'/>"
    "<property name='CanGoNext' type='b' access='read'/><property name='CanGoPrevious' type='b' access='read'/>"
    "</interface></node>";

static void bus_acquired(GDBusConnection *connection, const gchar *name, gpointer data) {
    (void)name; (void)data;
    bus = g_object_ref(connection);
    root_registration = g_dbus_connection_register_object(bus, path, node->interfaces[0], &vtable, NULL, NULL, NULL);
    player_registration = g_dbus_connection_register_object(bus, path, node->interfaces[1], &vtable, NULL, NULL, NULL);
    publish_mpris();
}

static const gchar *string(JsonObject *value, const gchar *key) {
    return json_object_get_string_member_with_default(value, key, "");
}
static double number(JsonObject *value, const gchar *key, double fallback) {
    return json_object_get_double_member_with_default(value, key, fallback);
}
static gboolean boolean(JsonObject *value, const gchar *key) {
    return json_object_get_boolean_member_with_default(value, key, FALSE);
}
static void set_string(gchar **destination, const gchar *value) { g_free(*destination); *destination = g_strdup(value); }

static void receive(JsonObject *message) {
    const gchar *command_name = string(message, "command");
    if (!strcmp(command_name, "initialize")) {
        clear_utterance();
        g_atomic_int_inc((gint *)&generation);
        if (speech) { spd_close(speech); speech = NULL; }
        if (number(message, "version", 0) != 1) { emit("error", NULL, "message", "Unsupported speech protocol"); return; }
        speech = spd_open("Zhihu++", "reading", NULL, SPD_MODE_THREADED);
        if (!speech) { emit("error", NULL, "message", "Speech Dispatcher is unavailable; install and start the system speech service"); return; }
        speech->callback_begin = speech_event;
        speech->callback_end = speech_event;
        speech->callback_cancel = speech_event;
        speech->callback_pause = speech_event;
        speech->callback_resume = speech_event;
        spd_set_notification_on(speech, SPD_BEGIN | SPD_END | SPD_CANCEL | SPD_PAUSE | SPD_RESUME);
        if (spd_set_language(speech, string(message, "language")) < 0) { emit("error", NULL, "message", "Speech language is unavailable"); return; }
        rate = CLAMP(number(message, "speed", 1), 0.5, 2.0);
        emit("ready", NULL, "label", "Speech Dispatcher");
        return;
    }
    if (!speech) { emit("error", string(message, "id"), "message", "Speech Dispatcher is unavailable"); return; }
    if (!strcmp(command_name, "speak")) {
        clear_utterance();
        const gchar *text = string(message, "text");
        const gchar *id = string(message, "id");
        if (!*id || !*text || strlen(text) > 131072) { emit("error", id, "message", "Invalid speech request"); return; }
        rate = CLAMP(number(message, "speed", rate), 0.5, 2.0);
        // Speech Dispatcher rates are percentages around the configured voice's normal rate.
        int voice_rate = rate >= 1 ? (int)lround((rate - 1) * 100) : (int)lround((rate - 1) * 200);
        if (spd_set_voice_rate(speech, voice_rate) < 0) { emit("error", id, "message", "Speech rate could not be configured"); return; }
        active_id = g_strdup(id);
        active_message = spd_say(speech, SPD_TEXT, text);
        if (active_message < 0) { emit("error", id, "message", "Speech synthesis failed"); g_clear_pointer(&active_id, g_free); }
    } else if (!strcmp(command_name, "pause")) {
        set_string(&pause_request, string(message, "request"));
        pause_message = active_message;
        if (active_message < 0) acknowledge_pause();
        else if (spd_pause(speech) < 0) emit("error", active_id, "message", "Speech could not be paused");
    } else if (!strcmp(command_name, "resume")) {
        if (spd_resume(speech) < 0) emit("error", active_id, "message", "Speech could not be resumed");
    } else if (!strcmp(command_name, "stop")) {
        clear_utterance();
    } else if (!strcmp(command_name, "speed")) {
        rate = CLAMP(number(message, "speed", 1), 0.5, 2.0);
    } else if (!strcmp(command_name, "metadata")) {
        active_session = boolean(message, "active"); playing = boolean(message, "playing");
        can_previous = boolean(message, "previous"); can_next = boolean(message, "next");
        set_string(&title, string(message, "title")); set_string(&author, string(message, "author")); set_string(&album, string(message, "album"));
        publish_mpris();
    } else emit("error", NULL, "message", "Unknown speech command");
}

static gboolean input_ready(GIOChannel *channel, GIOCondition condition, gpointer data) {
    (void)data;
    if (condition & G_IO_IN) {
        gchar *line = NULL;
        gsize length = 0;
        GIOStatus status = g_io_channel_read_line(channel, &line, &length, NULL, NULL);
        if (status == G_IO_STATUS_NORMAL) {
            JsonParser *parser = json_parser_new();
            if (length > 262144 || !json_parser_load_from_data(parser, line, length, NULL) || !JSON_NODE_HOLDS_OBJECT(json_parser_get_root(parser))) {
                emit("error", NULL, "message", "Invalid speech protocol");
                g_main_loop_quit(loop);
            } else receive(json_node_get_object(json_parser_get_root(parser)));
            g_object_unref(parser); g_free(line);
            return G_SOURCE_CONTINUE;
        }
        g_free(line);
        if (status == G_IO_STATUS_AGAIN) return G_SOURCE_CONTINUE;
    }
    g_main_loop_quit(loop);
    return G_SOURCE_REMOVE;
}

int main(void) {
    loop = g_main_loop_new(NULL, FALSE);
    node = g_dbus_node_info_new_for_xml(introspection, NULL);
    gchar *name = g_strdup_printf("org.mpris.MediaPlayer2.zhihupp.instance%d", (int)getpid());
    bus_owner = g_bus_own_name(G_BUS_TYPE_SESSION, name, G_BUS_NAME_OWNER_FLAGS_NONE, bus_acquired, NULL, NULL, NULL, NULL);
    g_free(name);
    GIOChannel *input = g_io_channel_unix_new(STDIN_FILENO);
    g_io_channel_set_encoding(input, NULL, NULL);
    g_io_channel_set_flags(input, G_IO_FLAG_NONBLOCK, NULL);
    g_io_add_watch(input, G_IO_IN | G_IO_HUP | G_IO_ERR, input_ready, NULL);
    g_main_loop_run(loop);
    clear_utterance();
    if (speech) spd_close(speech);
    if (bus) {
        if (root_registration) g_dbus_connection_unregister_object(bus, root_registration);
        if (player_registration) g_dbus_connection_unregister_object(bus, player_registration);
        g_object_unref(bus);
    }
    g_bus_unown_name(bus_owner);
    g_dbus_node_info_unref(node);
    g_io_channel_unref(input);
    g_main_loop_unref(loop);
    g_free(title); g_free(author); g_free(album); g_free(pause_request);
    return 0;
}
