package com.ferzdevzgta.game.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.SAMP;
import com.ferzdevzgta.game.net.ServerQueryClient;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.servers.ServerEndpoint;
import com.ferzdevzgta.game.servers.ServerList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the server this client connects to.
 *
 * The native client reads its target from {@code SAMP/settings.ini} once, on the first frame, so
 * this screen is the only place a connection is chosen. It writes that file through
 * {@link GameConfig}, which also creates the file on a fresh install, and then starts the game.
 */
public class ServerBrowserActivity extends AppCompatActivity {

    private final List<ServerEndpoint> servers = new ArrayList<>();
    /** One status line per listed address, so a probe started before a reload lands nowhere. */
    private final Map<String, TextView> liveSlots = new LinkedHashMap<>();

    private EditText nicknameField;
    private EditText nameField;
    private EditText hostField;
    private EditText portField;
    private LinearLayout listBox;
    private LinearLayout statusPillSlot;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.paintWindow(this);
        setContentView(buildUi());
        Ui.applyInsets(this, findViewById(android.R.id.content));
        reload();
    }

    private ScrollView buildUi() {
        LinearLayout root = Ui.column(this);

        root.addView(Ui.eyebrow(this, getString(R.string.app_name)));
        Ui.gap(this, root, 6);
        root.addView(Ui.title(this, getString(R.string.servers_title)));
        Ui.gap(this, root, 8);
        root.addView(Ui.body(this, getString(R.string.servers_intro)));

        Ui.gap(this, root, 20);
        root.addView(nicknameCard(), Ui.fill());
        Ui.gap(this, root, 12);
        root.addView(listCard(), Ui.fill());
        Ui.gap(this, root, 12);
        root.addView(addCard(), Ui.fill());

        Ui.gap(this, root, 16);
        LinearLayout statusRow = Ui.row(this);
        statusPillSlot = new LinearLayout(this);
        statusPillSlot.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.addView(statusPillSlot);
        TextView spacer = new TextView(this);
        statusRow.addView(spacer, Ui.weight(this, 1f, 0f));
        root.addView(statusRow);
        Ui.gap(this, root, 6);
        statusText = Ui.body(this, "");
        root.addView(statusText);

        return Ui.scroll(this, root);
    }

    private LinearLayout nicknameCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, getString(R.string.servers_section_identity)));
        nicknameField = Ui.input(this, getString(R.string.servers_nickname_hint),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nicknameField.setSingleLine(true);
        String saved = GameConfig.readNickname();
        nicknameField.setText(saved == null ? "" : saved);
        card.addView(nicknameField, Ui.fill());
        Ui.gap(this, card, 8);
        card.addView(Ui.label(this, getString(R.string.servers_nickname_hint)));
        return card;
    }

    private LinearLayout listCard() {
        LinearLayout card = Ui.card(this);
        LinearLayout head = Ui.row(this);
        head.addView(Ui.section(this, getString(R.string.servers_list)), Ui.weight(this, 1f, 8f));
        Button refresh = Ui.button(this, getString(R.string.servers_refresh), Ui.GHOST,
                R.drawable.ic_refresh);
        refresh.setOnClickListener(v -> probeAll());
        head.addView(refresh);
        card.addView(head, Ui.fill());
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(listBox, Ui.fill());
        return card;
    }

    /**
     * Asks every listed server for its info answer. The socket work runs off the main thread, so
     * a dead address costs one timeout instead of a frozen list, and each row updates on its own.
     */
    private void probeAll() {
        for (Map.Entry<String, TextView> entry : liveSlots.entrySet()) {
            entry.getValue().setText(getString(R.string.servers_probe_running));
            entry.getValue().setTextColor(Ui.FAINT);
        }
        for (ServerEndpoint server : servers) {
            TextView slot = liveSlots.get(server.address());
            if (slot == null) {
                continue;
            }
            ServerQueryClient.probe(server.host(), server.port(), answer -> {
                if (answer.ok) {
                    slot.setText(getString(R.string.servers_probe_ok, answer.latencyMs,
                            answer.info.players, answer.info.maxPlayers));
                    slot.setTextColor(answer.info.players >= answer.info.maxPlayers
                            ? Ui.WARN : Ui.OK);
                } else {
                    slot.setText(getString(R.string.servers_probe_offline));
                    slot.setTextColor(Ui.FAINT);
                }
            });
        }
    }

    private LinearLayout addCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.section(this, getString(R.string.servers_add)));

        nameField = field(card, R.string.servers_add_name, false);
        Ui.gap(this, card, 8);
        hostField = field(card, R.string.servers_add_host, false);
        Ui.gap(this, card, 8);
        portField = field(card, R.string.servers_add_port, true);

        Ui.gap(this, card, 14);
        Button add = Ui.button(this, getString(R.string.servers_add_action), Ui.SECONDARY, R.drawable.ic_server);
        add.setOnClickListener(v -> addCurrentFields());
        card.addView(add, Ui.fill());
        return card;
    }

    private EditText field(LinearLayout parent, int hint, boolean numeric) {
        EditText field = Ui.input(this, getString(hint), numeric
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setSingleLine(true);
        parent.addView(field, Ui.fill());
        return field;
    }

    private void reload() {
        servers.clear();
        servers.addAll(readServers());
        ServerEndpoint saved = GameConfig.readServer();
        boolean savedUsable = saved != null && !"127.0.0.1".equals(saved.host());
        if (servers.isEmpty() && savedUsable) {
            servers.add(saved);
        }
        listBox.removeAllViews();
        liveSlots.clear();
        if (servers.isEmpty()) {
            // An empty list is a first step, not a failure: the card names the one action that
            // fills it instead of printing a sentence in the middle of the screen.
            listBox.addView(Ui.emptyState(this, R.drawable.ic_server,
                    getString(R.string.servers_empty_title),
                    getString(R.string.servers_empty),
                    getString(R.string.servers_add),
                    v -> focusAddFields()), Ui.fill());
            return;
        }
        int index = 0;
        for (ServerEndpoint server : servers) {
            boolean active = savedUsable && saved.address().equals(server.address());
            LinearLayout block = serverBlock(server, active);
            listBox.addView(block, Ui.fill());
            Ui.fadeIn(block, index++);
        }
        probeAll();
    }

    private LinearLayout serverBlock(ServerEndpoint server, boolean active) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));

        LinearLayout header = Ui.row(this);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(Ui.value(this, server.name()));
        labels.addView(Ui.mono(this, server.address()));
        TextView live = Ui.mono(this, getString(R.string.servers_probe_running));
        live.setTextColor(Ui.FAINT);
        labels.addView(live);
        liveSlots.put(server.address(), live);
        header.addView(labels, Ui.weight(this, 1f, 8f));
        if (active) {
            header.addView(Ui.pill(this, getString(R.string.servers_current), Ui.OK));
        }
        block.addView(header, Ui.fill());

        Ui.gap(this, block, 10);
        LinearLayout actions = Ui.row(this);
        Button connect = Ui.button(this, getString(R.string.servers_connect), Ui.PRIMARY, R.drawable.ic_play);
        connect.setOnClickListener(v -> connect(server));
        actions.addView(connect, Ui.weight(this, 1f, 8f));
        Button remove = Ui.button(this, getString(R.string.servers_remove), Ui.GHOST);
        remove.setOnClickListener(v -> remove(server));
        actions.addView(remove);
        block.addView(actions, Ui.fill());
        return block;
    }

    /** Sends the eye to the add form when the empty state is tapped, since the form is below. */
    private void focusAddFields() {
        if (hostField != null) {
            hostField.requestFocus();
            hostField.setSelection(hostField.getText().length());
        }
    }

    private void addCurrentFields() {
        String name = nameField.getText().toString();
        String host = hostField.getText().toString();
        String portText = portField.getText().toString().trim();
        int port = ServerEndpoint.DEFAULT_PORT;
        if (!portText.isEmpty()) {
            try {
                port = Integer.parseInt(portText);
            } catch (NumberFormatException notANumber) {
                showError(getString(R.string.servers_bad_port), Ui.ERR);
                return;
            }
        }
        ServerEndpoint endpoint;
        try {
            endpoint = new ServerEndpoint(name, host, port);
        } catch (IllegalArgumentException rejected) {
            showError(rejected.getMessage(), Ui.ERR);
            return;
        }
        servers.clear();
        servers.addAll(ServerList.addOrReplace(readServers(), endpoint));
        writeServers();
        nameField.setText("");
        hostField.setText("");
        portField.setText("");
        reload();
        showError(getString(R.string.servers_added, endpoint.address()), Ui.OK);
    }

    private void remove(ServerEndpoint server) {
        List<ServerEndpoint> remaining = new ArrayList<>();
        for (ServerEndpoint candidate : readServers()) {
            if (!candidate.address().equals(server.address())) {
                remaining.add(candidate);
            }
        }
        try {
            writeServers(remaining);
            reload();
            showError(getString(R.string.servers_removed, server.address()), Ui.MUTED);
        } catch (IOException failure) {
            showError(getString(R.string.servers_write_failed, failure.getMessage()), Ui.ERR);
        }
    }

    private void connect(ServerEndpoint server) {
        String nickname = nicknameField.getText().toString();
        try {
            GameConfig.save(nickname, server, null);
        } catch (IllegalArgumentException rejected) {
            showError(rejected.getMessage(), Ui.ERR);
            return;
        } catch (IOException failure) {
            showError(getString(R.string.servers_write_failed, failure.getMessage()), Ui.ERR);
            return;
        }
        Toast.makeText(this, getString(R.string.servers_connecting, server.address()),
                Toast.LENGTH_SHORT).show();
        try {
            startActivity(new Intent(this, SAMP.class));
            finish();
        } catch (Exception cannotStart) {
            showError(String.valueOf(cannotStart.getMessage()), Ui.ERR);
        }
    }

    private List<ServerEndpoint> readServers() {
        File file = GameConfig.serversFile();
        if (!file.isFile()) {
            return new ArrayList<>();
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return new ArrayList<>(ServerList.parse(text));
        } catch (IOException unreadable) {
            return new ArrayList<>();
        }
    }

    private void writeServers() {
        try {
            writeServers(servers);
        } catch (IOException failure) {
            showError(getString(R.string.servers_write_failed, failure.getMessage()), Ui.ERR);
        }
    }

    private void writeServers(List<ServerEndpoint> list) throws IOException {
        File file = GameConfig.serversFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("cannot create " + parent);
        }
        Files.write(file.toPath(), ServerList.serialize(list).getBytes(StandardCharsets.UTF_8));
    }

    private void showError(String message, int tone) {
        if (statusPillSlot != null) {
            statusPillSlot.removeAllViews();
            statusPillSlot.addView(Ui.pill(this, tone == Ui.ERR
                    ? getString(R.string.data_state_error)
                    : getString(R.string.data_state_ok), tone));
        }
        if (statusText != null) {
            statusText.setText(message == null ? getString(R.string.servers_bad_input) : message);
            statusText.setTextColor(tone == Ui.MUTED ? Ui.MUTED : tone);
        }
    }
}
