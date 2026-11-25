package com.ferzdevzgta.game.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ferzdevzgta.game.R;
import com.ferzdevzgta.game.SAMP;
import com.ferzdevzgta.game.servers.GameConfig;
import com.ferzdevzgta.game.servers.ServerEndpoint;
import com.ferzdevzgta.game.servers.ServerList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks the server this client connects to.
 *
 * The native client reads its target from {@code SAMP/settings.ini} once, on the first frame, so
 * this screen is the only place a connection is chosen. It writes that file through
 * {@link GameConfig}, which also creates the file on a fresh install, and then starts the game.
 */
public class ServerBrowserActivity extends AppCompatActivity {

    private final List<ServerEndpoint> servers = new ArrayList<>();

    private EditText nicknameField;
    private EditText nameField;
    private EditText hostField;
    private EditText portField;
    private LinearLayout listBox;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.screen(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24));

        root.addView(Ui.title(this, getString(R.string.servers_title)));
        Ui.gap(this, root, 6);
        root.addView(Ui.body(this, getString(R.string.servers_intro)));
        Ui.gap(this, root, 14);

        root.addView(nicknameCard());
        Ui.gap(this, root, 12);
        root.addView(listCard());
        Ui.gap(this, root, 12);
        root.addView(addCard());
        Ui.gap(this, root, 12);

        status = Ui.body(this, "");
        status.setTextColor(Ui.MUTED);
        root.addView(status);

        scroll.addView(root);
        setContentView(scroll);

        reload();
    }

    private View nicknameCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.fieldLabel(this, getString(R.string.servers_nickname)));
        Ui.gap(this, card, 6);
        nicknameField = new EditText(this);
        nicknameField.setSingleLine(true);
        nicknameField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        nicknameField.setTextColor(Ui.TEXT);
        String saved = GameConfig.readNickname();
        nicknameField.setText(saved == null ? "" : saved);
        nicknameField.setHint(R.string.servers_nickname_hint);
        card.addView(nicknameField, Ui.fill());
        return card;
    }

    private View listCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.fieldLabel(this, getString(R.string.servers_list)));
        Ui.gap(this, card, 6);
        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(listBox, Ui.fill());
        return card;
    }

    private View addCard() {
        LinearLayout card = Ui.card(this);
        card.addView(Ui.fieldLabel(this, getString(R.string.servers_add)));
        Ui.gap(this, card, 6);

        nameField = textField(card, R.string.servers_add_name, false);
        hostField = textField(card, R.string.servers_add_host, false);
        portField = textField(card, R.string.servers_add_port, true);

        Ui.gap(this, card, 10);
        Button add = Ui.button(this, getString(R.string.servers_add_action), false);
        add.setOnClickListener(v -> addCurrentFields());
        card.addView(add, Ui.fill());
        return card;
    }

    private EditText textField(LinearLayout parent, int hint, boolean numeric) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setTextColor(Ui.TEXT);
        field.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        parent.addView(field, Ui.fill());
        return field;
    }

    private void reload() {
        servers.clear();
        servers.addAll(readServers());
        if (servers.isEmpty()) {
            ServerEndpoint saved = GameConfig.readServer();
            if (saved != null && !"127.0.0.1".equals(saved.host())) {
                servers.add(saved);
            }
        }
        listBox.removeAllViews();
        if (servers.isEmpty()) {
            listBox.addView(Ui.body(this, getString(R.string.servers_empty)));
            return;
        }
        for (ServerEndpoint server : servers) {
            listBox.addView(row(server), Ui.fill());
        }
    }

    private View row(ServerEndpoint server) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.fieldLabel(this, server.name());
        TextView address = Ui.mono(this, server.address());
        labels.addView(name);
        labels.addView(address);

        LinearLayout.LayoutParams wide = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(labels, wide);

        Button connect = Ui.button(this, getString(R.string.servers_connect), true);
        connect.setOnClickListener(v -> connect(server));
        row.addView(connect);

        Button remove = Ui.button(this, getString(R.string.servers_remove), false);
        remove.setOnClickListener(v -> remove(server));
        LinearLayout.LayoutParams narrow = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        narrow.setMargins(Ui.dp(this, 8), 0, 0, 0);
        row.addView(remove, narrow);
        return row;
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
                showError(getString(R.string.servers_bad_port));
                return;
            }
        }
        ServerEndpoint endpoint;
        try {
            endpoint = new ServerEndpoint(name, host, port);
        } catch (IllegalArgumentException rejected) {
            showError(rejected.getMessage());
            return;
        }
        servers.clear();
        servers.addAll(ServerList.addOrReplace(readServers(), endpoint));
        writeServers();
        nameField.setText("");
        hostField.setText("");
        portField.setText("");
        reload();
        status.setText(getString(R.string.servers_added, endpoint.address()));
        status.setTextColor(Ui.OK);
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
            status.setText(getString(R.string.servers_removed, server.address()));
            status.setTextColor(Ui.MUTED);
        } catch (IOException failure) {
            showError(getString(R.string.servers_write_failed, failure.getMessage()));
        }
    }

    private void connect(ServerEndpoint server) {
        String nickname = nicknameField.getText().toString();
        try {
            GameConfig.save(nickname, server, null);
        } catch (IllegalArgumentException rejected) {
            showError(rejected.getMessage());
            return;
        } catch (IOException failure) {
            showError(getString(R.string.servers_write_failed, failure.getMessage()));
            return;
        }
        Toast.makeText(this, getString(R.string.servers_connecting, server.address()), Toast.LENGTH_SHORT).show();
        try {
            startActivity(new Intent(this, SAMP.class));
            finish();
        } catch (Exception cannotStart) {
            showError(String.valueOf(cannotStart.getMessage()));
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
            showError(getString(R.string.servers_write_failed, failure.getMessage()));
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

    private void showError(String message) {
        status.setText(message == null ? getString(R.string.servers_bad_input) : message);
        status.setTextColor(Ui.ERR);
    }
}
