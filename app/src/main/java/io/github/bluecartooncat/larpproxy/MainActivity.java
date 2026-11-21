package io.github.bluecartooncat.larpproxy;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 27;
    private static final String PREFS = "larp_proxy";
    private static final String PORT_PREF = "listen_port";
    private static final String MODE_PREF = "transport";
    private static final String TCP = "tcp";
    private static final String WS = "ws";
    private static final int BG = Color.rgb(6, 6, 7);
    private static final int MUTED = Color.rgb(155, 155, 162);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView stateText;
    private TextView addressText;
    private TextView infoText;
    private Button toggleButton;
    private EditText portInput;
    private Button savePortButton;
    private RadioGroup modeGroup;
    private RadioButton tcpChoice, wsChoice;
    private boolean ticking;
    private String firstAddress;

    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            render();
            if (ticking) handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        configuredPort();
        buildScreen();
    }

    @Override protected void onResume() {
        super.onResume();
        ticking = true;
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        ticking = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -1));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.larp_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(112), dp(112));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        logoLp.bottomMargin = dp(18);
        body.addView(logo, logoLp);

        TextView title = text("LarpProxy", 29, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        body.addView(title, full(dp(8)));

        TextView description = text(
                "Share Android Auto's developer head unit server with a receiver on your local network.",
                14, MUTED, false);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(dp(3), 1f);
        body.addView(description, full(dp(26)));

        TextView portLabel = text("Listening port (saved)", 13, MUTED, false);
        portLabel.setGravity(Gravity.CENTER_HORIZONTAL);
        body.addView(portLabel, full(dp(8)));

        LinearLayout portRow = new LinearLayout(this);
        portRow.setOrientation(LinearLayout.HORIZONTAL);
        portRow.setGravity(Gravity.CENTER_VERTICAL);
        portRow.setPadding(0, 0, 0, dp(12));
        portInput = new EditText(this);
        portInput.setSingleLine(true);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setSelectAllOnFocus(true);
        portInput.setText(String.valueOf(configuredPort()));
        portInput.setTextColor(Color.WHITE);
        portInput.setTextSize(19);
        portInput.setGravity(Gravity.CENTER);
        portInput.setBackground(rounded(Color.rgb(28, 28, 31), 12));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        fieldParams.rightMargin = dp(10);
        portRow.addView(portInput, fieldParams);
        savePortButton = new Button(this);
        savePortButton.setText("Save");
        savePortButton.setAllCaps(false);
        savePortButton.setOnClickListener(v -> saveSettings());
        portRow.addView(savePortButton, new LinearLayout.LayoutParams(dp(115), dp(52)));
        body.addView(portRow, full(dp(5)));

        modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.HORIZONTAL);
        modeGroup.setGravity(Gravity.CENTER);
        tcpChoice = new RadioButton(this);
        tcpChoice.setId(View.generateViewId());
        tcpChoice.setText("TCP");
        tcpChoice.setTextColor(Color.WHITE);
        tcpChoice.setPadding(0, 0, dp(22), 0);
        wsChoice = new RadioButton(this);
        wsChoice.setId(View.generateViewId());
        wsChoice.setText("WebSocket");
        wsChoice.setTextColor(Color.WHITE);
        modeGroup.addView(tcpChoice);
        modeGroup.addView(wsChoice);
        modeGroup.check(configuredMode().equals(WS) ? wsChoice.getId() : tcpChoice.getId());
        body.addView(modeGroup, full(dp(24)));

        toggleButton = new Button(this);
        toggleButton.setAllCaps(false);
        toggleButton.setText("Start proxy");
        toggleButton.setTextSize(16);
        toggleButton.setTextColor(Color.BLACK);
        toggleButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        toggleButton.setBackground(rounded(Color.WHITE, 18));
        toggleButton.setOnClickListener(v -> {
            if (ProxyService.running || ProxyService.starting) {
                Intent stop = new Intent(this, ProxyService.class).setAction(ProxyService.STOP);
                startService(stop);
            } else {
                if (saveSettings()) askForNotificationThenStart();
            }
        });
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(54));
        buttonLp.bottomMargin = dp(26);
        body.addView(toggleButton, buttonLp);

        stateText = text("Stopped", 15, MUTED, true);
        stateText.setGravity(Gravity.CENTER);
        body.addView(stateText, full(dp(12)));

        addressText = text("", 21, Color.WHITE, true);
        addressText.setGravity(Gravity.CENTER);
        addressText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        addressText.setLineSpacing(dp(8), 1f);
        addressText.setPadding(dp(16), dp(18), dp(16), dp(18));
        addressText.setBackground(rounded(Color.rgb(20, 20, 22), 18));
        addressText.setOnClickListener(v -> {
            if (firstAddress != null) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("LarpProxy address", firstAddress));
                    Toast.makeText(this, "Address copied", Toast.LENGTH_SHORT).show();
                }
            }
        });
        body.addView(addressText, full(dp(12)));

        infoText = text(
                "On this phone, open Android Auto developer settings and tap “Start head unit server”. Then start the proxy and connect LarpPlay to the shown IP and port.",
                13, MUTED, false);
        infoText.setGravity(Gravity.CENTER);
        infoText.setLineSpacing(dp(3), 1f);
        body.addView(infoText, full(0));

        setContentView(scroll);
    }

    private int configuredPort() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        int port = prefs.getInt(PORT_PREF, 0);
        if (port >= 1024 && port <= 65535 && port != 5277) return port;
        port = 20000 + new SecureRandom().nextInt(40000);
        if (port == 5277) port = 5278;
        prefs.edit().putInt(PORT_PREF, port).apply();
        return port;
    }

    private String configuredMode() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(MODE_PREF, TCP).equals(WS) ? WS : TCP;
    }

    private boolean saveSettings() {
        if (ProxyService.running || ProxyService.starting) {
            Toast.makeText(this, "Stop the proxy before changing settings", Toast.LENGTH_SHORT).show();
            return false;
        }
        int port;
        try { port = Integer.parseInt(portInput.getText().toString().trim()); }
        catch (NumberFormatException e) { port = 0; }
        if (port < 1024 || port > 65535 || port == 5277) {
            portInput.setError("Choose an available port from 1024 to 65535, except 5277");
            return false;
        }
        String mode = modeGroup.getCheckedRadioButtonId() == wsChoice.getId() ? WS : TCP;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putInt(PORT_PREF, port).putString(MODE_PREF, mode).apply();
        Toast.makeText(this, "Port and mode saved", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void askForNotificationThenStart() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS },
                    NOTIFICATION_PERMISSION_REQUEST);
        } else {
            startProxy();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                      int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            startProxy();
        }
    }

    private void startProxy() {
        try {
            Intent intent = new Intent(this, ProxyService.class).setAction(ProxyService.START);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
            stateText.setText("Starting…");
        } catch (RuntimeException e) {
            stateText.setText("Could not start service: " + e.getMessage());
        }
    }

    private void render() {
        boolean active = ProxyService.running && ProxyService.listenPort > 0;
        boolean busy = ProxyService.starting;
        toggleButton.setText(active || busy ? "Stop proxy" : "Start proxy");
        portInput.setEnabled(!active && !busy);
        savePortButton.setEnabled(!active && !busy);
        tcpChoice.setEnabled(!active && !busy);
        wsChoice.setEnabled(!active && !busy);
        toggleButton.setEnabled(true);
        toggleButton.setBackground(rounded(active || busy ? Color.rgb(40, 40, 42) : Color.WHITE, 18));
        toggleButton.setTextColor(active || busy ? Color.WHITE : Color.BLACK);
        stateText.setText(ProxyService.status);

        if (active) {
            List<String> ips = lanAddresses();
            firstAddress = ips.isEmpty() ? null : ips.get(0) + ":" + ProxyService.listenPort;
            addressText.setVisibility(View.VISIBLE);
            StringBuilder addresses = new StringBuilder();
            if (ips.isEmpty()) {
                addresses.append("Listening on port ").append(ProxyService.listenPort)
                        .append("\nConnect Wi-Fi / hotspot to get a LAN IP");
            } else {
                for (int i = 0; i < ips.size(); i++) {
                    if (i > 0) addresses.append("\n");
                    addresses.append(ips.get(i)).append(":").append(ProxyService.listenPort);
                }
                addresses.append("\n\nTap to copy first address");
            }
            addressText.setText(addresses.toString());
            infoText.setText("Forwarding " + (ProxyService.webSocket ? "WebSocket" : "TCP") +
                    " to 127.0.0.1:5277 on this phone. " +
                    "Use only on a trusted network; connections have no access control or encryption at the proxy layer.");
        } else {
            firstAddress = null;
            addressText.setVisibility(View.GONE);
            infoText.setText("In Android Auto developer settings tap “Start head unit server”. " +
                    "Choose TCP for IWA Direct Sockets, or WebSocket for the PWA. " +
                    "Then enter this phone's IP and saved port in LarpPlay.");
        }
    }

    private static List<String> lanAddresses() {
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> all = NetworkInterface.getNetworkInterfaces();
            if (all != null) while (all.hasMoreElements()) {
                NetworkInterface iface = all.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()
                            && !addr.isLinkLocalAddress()) {
                        result.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) { }
        Collections.sort(result, (a, b) -> {
            int pa = a.startsWith("192.168.") ? 0 : a.startsWith("10.") ? 1 : 2;
            int pb = b.startsWith("192.168.") ? 0 : b.startsWith("10.") ? 1 : 2;
            return pa == pb ? a.compareTo(b) : Integer.compare(pa, pb);
        });
        return result;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private LinearLayout.LayoutParams full(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = bottomMargin;
        return p;
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp((int) radiusDp));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static final class ProxyService extends Service {
        static final String START = "io.github.bluecartooncat.larpproxy.START";
        static final String STOP = "io.github.bluecartooncat.larpproxy.STOP";
        private static final String CHANNEL = "larpproxy_foreground";
        private static final int NOTIFY_ID = 5277;

        static volatile boolean starting = false;
        static volatile boolean running = false;
        static volatile int listenPort = 0;
        static volatile boolean webSocket = false;
        static volatile String status = "Stopped";

        private volatile boolean keepGoing;
        private volatile ServerSocket listener;
        private volatile Socket receiver;
        private volatile Socket source;
        private volatile boolean hasClient;

        @Override public void onCreate() {
            super.onCreate();
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL, "LarpProxy service", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Keeps the Android Auto TCP proxy active");
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(channel);
            }
        }

        @Override public int onStartCommand(Intent intent, int flags, int startId) {
            if (intent != null && STOP.equals(intent.getAction())) {
                shutdown();
                stopSelf();
                return START_NOT_STICKY;
            }
            if (keepGoing) return START_STICKY;

            starting = true;
            running = false;
            status = "Starting proxy…";
            listenPort = 0;
            webSocket = WS.equals(getSharedPreferences(PREFS, MODE_PRIVATE).getString(MODE_PREF, TCP));
            keepGoing = true;

            try {
                startForeground(NOTIFY_ID, notification("Starting proxy…"));
            } catch (RuntimeException e) {
                keepGoing = false;
                starting = false;
                status = "Foreground service failed: " + e.getMessage();
                stopSelf();
                return START_NOT_STICKY;
            }

            Thread acceptor = new Thread(this::listenLoop, "LarpProxy-accept");
            acceptor.start();
            return START_STICKY;
        }

        private void listenLoop() {
            try {
                int desired = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(PORT_PREF, 0);
                if (desired < 1024 || desired > 65535 || desired == 5277)
                    throw new IOException("Invalid configured port");
                ServerSocket server = new ServerSocket();
                server.setReuseAddress(false);
                server.bind(new InetSocketAddress("0.0.0.0", desired), 16);
                listener = server;
                if (!keepGoing) { server.close(); return; }
                listenPort = server.getLocalPort();
                running = true;
                starting = false;
                status = "Listening (" + (webSocket ? "WebSocket" : "TCP") + ") • waiting for receiver";
                updateNotification();

                while (keepGoing) {
                    Socket client;
                    try { client = server.accept(); }
                    catch (IOException e) {
                        if (!keepGoing) break;
                        throw e;
                    }
                    client.setTcpNoDelay(true);
                    synchronized (this) {
                        if (!keepGoing || hasClient) {
                            closeQuietly(client);
                            continue;
                        }
                        hasClient = true;
                        receiver = client;
                    }
                    new Thread(() -> handleClient(client), "LarpProxy-session").start();
                }
            } catch (IOException e) {
                if (keepGoing) {
                    status = "Unable to listen on saved port: " + e.getMessage() + " (stop and change port)";
                    shutdown();
                    stopSelf();
                }
            }
        }

        private void handleClient(Socket client) {
            Socket upstream = new Socket();
            try {
                source = upstream;
                if (webSocket) acceptWebSocket(client);
                status = "Receiver connected • opening Android Auto server…";
                updateNotification();
                upstream.connect(new InetSocketAddress("127.0.0.1", 5277), 4000);
                upstream.setTcpNoDelay(true);
                status = "Receiver connected • forwarding " + (webSocket ? "WebSocket" : "TCP");
                updateNotification();

                Thread toPhone = new Thread(() -> {
                    if (webSocket) websocketToTcp(client, upstream);
                    else pipe(client, upstream);
                }, "LarpProxy-to-phone");
                Thread toReceiver = new Thread(() -> {
                    if (webSocket) tcpToWebsocket(upstream, client);
                    else pipe(upstream, client);
                }, "LarpProxy-to-receiver");
                toPhone.start();
                toReceiver.start();
                toPhone.join();
                toReceiver.join();
            } catch (IOException e) {
                if (keepGoing) status = "Receiver or Android Auto connection failed: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                closeQuietly(client);
                closeQuietly(upstream);
                synchronized (this) {
                    if (receiver == client) receiver = null;
                    if (source == upstream) source = null;
                    hasClient = false;
                }
                if (keepGoing && running) {
                    if (!status.startsWith("Receiver or Android Auto connection failed")) {
                        status = "Listening (" + (webSocket ? "WebSocket" : "TCP") + ") • waiting for receiver";
                    }
                    updateNotification();
                }
            }
        }

        private static void pipe(Socket from, Socket to) {
            try {
                InputStream input = from.getInputStream();
                OutputStream output = to.getOutputStream();
                byte[] buffer = new byte[32 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                output.flush();
            } catch (IOException ignored) { }
            finally { closeQuietly(from); closeQuietly(to); }
        }

        private static void acceptWebSocket(Socket client) throws IOException {
            client.setSoTimeout(7000);
            InputStream in = client.getInputStream();
            ByteArrayOutputStream headers = new ByteArrayOutputStream();
            int tail = 0;
            while (headers.size() < 8192) {
                int b = in.read();
                if (b < 0) throw new IOException("WebSocket handshake closed");
                headers.write(b);
                tail = (tail << 8) | (b & 255);
                if (tail == 0x0d0a0d0a) break;
            }
            if (tail != 0x0d0a0d0a) throw new IOException("WebSocket HTTP headers too large");
            String request = headers.toString("ISO-8859-1");
            String[] lines = request.split("\r\n");
            if (lines.length < 2 || !(lines[0].startsWith("GET /aa HTTP/1.1") || lines[0].startsWith("GET / HTTP/1.1")))
                throw new IOException("Invalid WebSocket request path");
            String key = null, upgrade = "", connection = "", version = "";
            for (String line : lines) {
                int idx = line.indexOf(':');
                if (idx < 0) continue;
                String name = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(idx+1).trim();
                if (name.equals("sec-websocket-key")) key = value;
                if (name.equals("upgrade")) upgrade = value;
                if (name.equals("connection")) connection = value;
                if (name.equals("sec-websocket-version")) version = value;
            }
            if (key == null || !upgrade.equalsIgnoreCase("websocket") ||
                    !connection.toLowerCase(Locale.ROOT).contains("upgrade") || !version.equals("13"))
                throw new IOException("Invalid WebSocket Upgrade headers");
            byte[] decoded;
            try { decoded = Base64.getDecoder().decode(key); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid WebSocket key"); }
            if (decoded.length != 16) throw new IOException("Invalid WebSocket key length");
            String accept;
            try {
                byte[] digest = MessageDigest.getInstance("SHA-1")
                        .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                .getBytes(StandardCharsets.US_ASCII));
                accept = Base64.getEncoder().encodeToString(digest);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException("SHA-1 unavailable", e);
            }
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();
            client.setSoTimeout(0);
        }

        private static void websocketToTcp(Socket client, Socket upstream) {
            try {
                InputStream in = client.getInputStream();
                OutputStream out = upstream.getOutputStream();
                OutputStream toBrowser = client.getOutputStream();
                boolean fragmented = false;
                while (true) {
                    int b0 = in.read(), b1 = in.read();
                    if (b0 < 0 || b1 < 0) break;
                    boolean fin = (b0 & 0x80) != 0;
                    int opcode = b0 & 15;
                    if ((b0 & 0x70) != 0 || (b1 & 0x80) == 0)
                        throw new IOException("Unsupported or unmasked WebSocket frame");
                    long length = b1 & 127;
                    if (length == 126) length = ((long) requireByte(in) << 8) | requireByte(in);
                    else if (length == 127) {
                        length = 0;
                        for (int i = 0; i < 8; i++) length = (length << 8) | requireByte(in);
                    }
                    if (length > 256 * 1024L || length < 0)
                        throw new IOException("WebSocket frame too large");
                    boolean control = opcode >= 8;
                    if (control && (!fin || length > 125)) throw new IOException("Invalid control frame");
                    byte[] mask = new byte[4], payload = new byte[(int) length];
                    readAll(in, mask);
                    readAll(in, payload);
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                    if (opcode == 8) {
                        writeWebSocketFrame(toBrowser, 8, payload, payload.length);
                        break;
                    } else if (opcode == 9) {
                        writeWebSocketFrame(toBrowser, 10, payload, payload.length);
                    } else if (opcode == 10) {
                    } else if (opcode == 2 || opcode == 0) {
                        if ((opcode == 0) != fragmented)
                            throw new IOException("Invalid WebSocket fragment sequence");
                        out.write(payload);
                        if (fin) fragmented = false;
                        else fragmented = true;
                    } else {
                        throw new IOException("Expected a binary WebSocket frame");
                    }
                }
            } catch (IOException ignored) { }
            finally { closeQuietly(client); closeQuietly(upstream); }
        }

        private static void tcpToWebsocket(Socket upstream, Socket client) {
            try {
                InputStream in = upstream.getInputStream();
                OutputStream out = client.getOutputStream();
                byte[] bytes = new byte[32 * 1024];
                int n;
                while ((n = in.read(bytes)) != -1) writeWebSocketFrame(out, 2, bytes, n);
            } catch (IOException ignored) { }
            finally { closeQuietly(client); closeQuietly(upstream); }
        }

        private static int requireByte(InputStream in) throws IOException {
            int b = in.read();
            if (b < 0) throw new IOException("Unexpected end of WebSocket frame");
            return b;
        }
        private static void readAll(InputStream in, byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                int n = in.read(bytes, offset, bytes.length - offset);
                if (n < 0) throw new IOException("Truncated WebSocket frame");
                offset += n;
            }
        }
        private static void writeWebSocketFrame(OutputStream out, int opcode, byte[] payload, int len)
                throws IOException {
            synchronized (out) {
                out.write(0x80 | opcode);
                if (len < 126) out.write(len);
                else if (len <= 65535) {
                    out.write(126); out.write(len >> 8); out.write(len);
                } else {
                    out.write(127);
                    long length = len;
                    for (int i = 7; i >= 0; i--) out.write((int) (length >>> (i*8)) & 255);
                }
                out.write(payload, 0, len);
                out.flush();
            }
        }

        private Notification notification(String message) {
            Intent open = new Intent(this, MainActivity.class);
            PendingIntent openAction = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Intent stop = new Intent(this, ProxyService.class).setAction(STOP);
            PendingIntent stopAction = PendingIntent.getService(this, 1, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL)
                    : new Notification.Builder(this);
            return builder.setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setContentTitle("LarpProxy")
                    .setContentText(message)
                    .setContentIntent(openAction)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .addAction(android.R.drawable.ic_media_pause, "Stop", stopAction)
                    .build();
        }

        private void updateNotification() {
            if (!keepGoing) return;
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) {
                String message = listenPort > 0
                        ? (hasClient ? "Connected • " : "Listening • ") +
                          (webSocket ? "WS " : "TCP ") + listenPort
                        : "Starting proxy…";
                nm.notify(NOTIFY_ID, notification(message));
            }
        }

        private synchronized void shutdown() {
            keepGoing = false;
            closeQuietly(listener);
            closeQuietly(receiver);
            closeQuietly(source);
            listener = null;
            receiver = null;
            source = null;
            hasClient = false;
            running = false;
            starting = false;
            listenPort = 0;
            if (!status.startsWith("Unable to listen") &&
                    !status.startsWith("Foreground service failed")) status = "Stopped";
        }

        @Override public void onDestroy() {
            shutdown();
            super.onDestroy();
        }

        private static void closeQuietly(java.io.Closeable c) {
            if (c != null) try { c.close(); } catch (IOException ignored) { }
        }

        @Override public IBinder onBind(Intent intent) { return null; }
    }
}
