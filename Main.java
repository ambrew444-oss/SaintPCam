import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class Main {
    private static final int PORT = 8080;
    private static final Path USERS_FILE = Path.of("data", "users.db");

    private final UserStore userStore = new UserStore(USERS_FILE);
    private final SessionStore sessions = new SessionStore();

    public static void main(String[] args) throws IOException {
        new Main().start();
    }

    private void start() throws IOException {
        userStore.load();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", this::route);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("Dating app backend started: http://localhost:" + PORT);
    }

    private void route(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && "/assets/placeholders/profile-card-placeholder.png".equals(path)) {
                sendFile(exchange, Path.of("assets", "placeholders", "profile-card-placeholder.png"), "image/png");
            } else if ("GET".equals(method) && "/".equals(path)) {
                handleHome(exchange);
            } else if ("GET".equals(method) && "/home".equals(path)) {
                handleDashboard(exchange);
            } else if ("GET".equals(method) && "/friends".equals(path)) {
                handleFriends(exchange, null, false);
            } else if ("POST".equals(method) && "/friends/add".equals(path)) {
                handleAddFriend(exchange);
            } else if ("GET".equals(method) && "/dev/neutral-login".equals(path)) {
                handleNeutralDevLogin(exchange);
            } else if ("GET".equals(method) && "/register".equals(path)) {
                sendHtml(exchange, page("Регистрация", registrationLanding(null)));
            } else if ("POST".equals(method) && "/register".equals(path)) {
                handleRegister(exchange);
            } else if ("GET".equals(method) && "/login".equals(path)) {
                sendHtml(exchange, page("Вход", loginLanding(null)));
            } else if ("POST".equals(method) && "/login".equals(path)) {
                handleLogin(exchange);
            } else if ("POST".equals(method) && "/logout".equals(path)) {
                handleLogout(exchange);
            } else if ("GET".equals(method) && "/profile".equals(path)) {
                handleProfileForm(exchange, null);
            } else if ("POST".equals(method) && "/profile".equals(path)) {
                handleProfileSave(exchange);
            } else {
                sendHtml(exchange, 404, page("Не найдено", "<section class=\"panel\"><h1>Страница не найдена</h1><a href=\"/\">На главную</a></section>"));
            }
        } catch (RuntimeException error) {
            error.printStackTrace();
            sendHtml(exchange, 500, page("Ошибка", "<section class=\"panel\"><h1>Что-то пошло не так</h1><p>Попробуйте повторить действие позже.</p></section>"));
        } finally {
            exchange.close();
        }
    }

    private void handleHome(HttpExchange exchange) throws IOException {
        Optional<User> currentUser = currentUser(exchange);
        if (currentUser.isPresent()) {
            redirect(exchange, "/home");
            return;
        }

        sendHtml(exchange, page("Регистрация", registrationLanding(null)));
    }

    private void handleNeutralDevLogin(HttpExchange exchange) throws IOException {
        if (!exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
            sendHtml(exchange, 404, page("Не найдено", "<section class=\"panel\"><h1>Страница не найдена</h1><a href=\"/\">На главную</a></section>"));
            return;
        }

        User user = userStore.findByEmail("neutral@example.local")
                .orElseGet(() -> userStore.register("neutral@example.local", "Neutral User", Passwords.hash(UUID.randomUUID().toString()))
                        .orElseThrow());
        createSession(exchange, user);
        redirect(exchange, "/home");
    }

    private void handleDashboard(HttpExchange exchange) throws IOException {
        Optional<User> currentUser = requireUser(exchange);
        if (currentUser.isEmpty()) {
            return;
        }

        String mode = queryParam(exchange, "mode").orElse("");
        String body = switch (mode) {
            case "swipe" -> swipeMode();
            case "2v2" -> twoVTwoMode();
            case "date-ideas" -> dateIdeasMode();
            default -> homeChats(currentUser.get());
        };

        sendHtml(exchange, page("Главная", body, currentUser, mode));
    }

    private void handleFriends(HttpExchange exchange, String message, boolean success) throws IOException {
        Optional<User> currentUser = requireUser(exchange);
        if (currentUser.isEmpty()) {
            return;
        }

        sendHtml(exchange, page("Друзья", friendsPage(currentUser.get(), message, success), currentUser));
    }

    private void handleAddFriend(HttpExchange exchange) throws IOException {
        Optional<User> currentUser = requireUser(exchange);
        if (currentUser.isEmpty()) {
            return;
        }

        Map<String, String> form = parseForm(exchange);
        FriendAddResult result = userStore.addFriendByCode(currentUser.get().email(), clean(form.get("friendCode")));
        String message = switch (result) {
            case ADDED -> "Друг добавлен. Теперь можно начать чат на Home.";
            case ALREADY_FRIENDS -> "Вы уже друзья.";
            case SELF -> "Это ваш код. Для добавления нужен код другого человека.";
            case NOT_FOUND -> "Не нашли пользователя с таким кодом дружбы.";
        };

        handleFriends(exchange, message, result == FriendAddResult.ADDED || result == FriendAddResult.ALREADY_FRIENDS);
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(exchange);
        String email = normalizeEmail(form.get("email"));
        String name = clean(form.get("name"));
        String password = form.getOrDefault("password", "");

        if (email.isBlank() || name.isBlank() || password.length() < 8) {
            sendHtml(exchange, 400, page("Регистрация", registrationLanding("Введите имя, корректный email и пароль минимум из 8 символов.")));
            return;
        }

        Optional<User> created = userStore.register(email, name, Passwords.hash(password));
        if (created.isEmpty()) {
            sendHtml(exchange, 409, page("Регистрация", registrationLanding("Пользователь с таким email уже зарегистрирован.")));
            return;
        }

        createSession(exchange, created.get());
        redirect(exchange, "/home");
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> form = parseForm(exchange);
        String email = normalizeEmail(form.get("email"));
        String password = form.getOrDefault("password", "");

        Optional<User> user = userStore.findByEmail(email);
        if (user.isEmpty() || !Passwords.verify(password, user.get().passwordHash())) {
            sendHtml(exchange, 401, page("Вход", loginLanding("Неверный email или пароль.")));
            return;
        }

        createSession(exchange, user.get());
        redirect(exchange, "/home");
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        readCookie(exchange, "SESSION").ifPresent(sessions::delete);
        exchange.getResponseHeaders().add("Set-Cookie", "SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        redirect(exchange, "/");
    }

    private void handleProfileForm(HttpExchange exchange, String message) throws IOException {
        Optional<User> user = requireUser(exchange);
        if (user.isEmpty()) {
            return;
        }

        sendHtml(exchange, page("Анкета", profileForm(user.get(), message), user));
    }

    private void handleProfileSave(HttpExchange exchange) throws IOException {
        Optional<User> user = requireUser(exchange);
        if (user.isEmpty()) {
            return;
        }

        Map<String, String> form = parseForm(exchange);
        Profile profile = new Profile(
                clean(form.get("age")),
                clean(form.get("city")),
                clean(form.get("goal")),
                clean(form.get("interests")),
                clean(form.get("about"))
        );

        userStore.updateProfile(user.get().email(), profile);
        handleProfileForm(exchange, "Анкета сохранена.");
    }

    private Optional<User> requireUser(HttpExchange exchange) throws IOException {
        Optional<User> user = currentUser(exchange);
        if (user.isEmpty()) {
            redirect(exchange, "/");
        }
        return user;
    }

    private Optional<User> currentUser(HttpExchange exchange) {
        return readCookie(exchange, "SESSION")
                .flatMap(sessions::emailFor)
                .flatMap(userStore::findByEmail);
    }

    private void createSession(HttpExchange exchange, User user) {
        String sessionId = sessions.create(user.email());
        exchange.getResponseHeaders().add("Set-Cookie", "SESSION=" + sessionId + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800");
    }

    private String registrationLanding(String error) {
        return """
                <section class="auth-screen">
                    <div class="auth-window">
                        %s
                    </div>
                </section>
                """.formatted(registerForm(error));
    }

    private String loginLanding(String error) {
        return """
                <section class="auth-screen">
                    <div class="auth-window">
                        %s
                    </div>
                </section>
                """.formatted(loginForm(error));
    }

    private String registerForm(String error) {
        return formPanel("Регистрация", error, """
                <form class="form" method="post" action="/register">
                    <label>Имя<input name="name" autocomplete="name" required></label>
                    <label>Email<input name="email" type="email" autocomplete="email" required></label>
                    <label>Пароль<input name="password" type="password" autocomplete="new-password" minlength="8" required></label>
                    <button class="button primary" type="submit">Зарегистрироваться</button>
                    <p class="muted auth-switch">Уже есть аккаунт? <a href="/login">Войти</a></p>
                </form>
                """);
    }

    private String loginForm(String error) {
        return formPanel("Войти", error, """
                <form class="form" method="post" action="/login">
                    <label>Email<input name="email" type="email" autocomplete="email" required></label>
                    <label>Пароль<input name="password" type="password" autocomplete="current-password" required></label>
                    <button class="button primary" type="submit">Войти</button>
                    <p class="muted auth-switch">Нет аккаунта? <a href="/">Зарегистрироваться</a></p>
                </form>
                """);
    }

    private String profileForm(User user, String message) {
        Profile profile = user.profile();
        String notice = message == null ? "" : "<p class=\"success\">" + escapeHtml(message) + "</p>";
        return """
                <section class="panel">
                    <div class="panel-head">
                        <div>
                            <p class="eyebrow">Моя анкета</p>
                            <h1>%s</h1>
                        </div>
                        <a class="button ghost" href="/home">На главную</a>
                    </div>
                    %s
                    <form class="form" method="post" action="/profile">
                        <label>Возраст<input name="age" inputmode="numeric" value="%s" placeholder="Например, 28"></label>
                        <label>Город<input name="city" value="%s" placeholder="Москва"></label>
                        <label>Цель знакомства
                            <select name="goal">
                                %s
                            </select>
                        </label>
                        <label>Интересы<textarea name="interests" rows="3" placeholder="Кино, бег, путешествия">%s</textarea></label>
                        <label>О себе<textarea name="about" rows="5" placeholder="Несколько живых строк о себе">%s</textarea></label>
                        <button class="button primary" type="submit">Сохранить анкету</button>
                    </form>
                </section>
                """.formatted(
                escapeHtml(user.name()),
                notice,
                escapeHtml(profile.age()),
                escapeHtml(profile.city()),
                goalOptions(profile.goal()),
                escapeHtml(profile.interests()),
                escapeHtml(profile.about())
        );
    }

    private String formPanel(String title, String error, String form) {
        String errorBlock = error == null ? "" : "<p class=\"error\">" + escapeHtml(error) + "</p>";
        return """
                <section class="panel narrow">
                    <h1>%s</h1>
                    %s
                    %s
                </section>
                """.formatted(escapeHtml(title), errorBlock, form);
    }

    private String heroVisual() {
        return """
                <div class="hero-visual" aria-label="Панель активности WFL">
                    <div class="visual-topline"><span></span><span></span><span></span><b>Match OS</b></div>
                    <div class="visual-main">
                        <small>Активность профиля</small>
                        <strong>86%</strong>
                    </div>
                    <div class="visual-grid">
                        <div><small>Анкета</small><b>Готова</b></div>
                        <div><small>Интересы</small><b>12</b></div>
                        <div><small>Сессия</small><b>Live</b></div>
                    </div>
                    <div class="visual-progress"><span></span></div>
                </div>
                """;
    }

    private String goalOptions(String selected) {
        List<String> goals = List.of("Серьезные отношения", "Общение", "Дружба", "Пока присматриваюсь");
        StringBuilder html = new StringBuilder();
        for (String goal : goals) {
            String selectedAttr = goal.equals(selected) ? " selected" : "";
            html.append("<option").append(selectedAttr).append(">")
                    .append(escapeHtml(goal))
                    .append("</option>");
        }
        return html.toString();
    }

    private String homeChats(User user) {
        List<User> friends = userStore.friendsOf(user);
        if (friends.isEmpty()) {
            return """
                    <section class="chat-home empty-chat-home">
                        <div class="chat-hero">
                            <p class="eyebrow">Home</p>
                            <h1>Чаты с друзьями</h1>
                            <p>
                                Здесь появятся личные чаты, когда вы добавите первых друзей.
                                Обменяйтесь кодом дружбы и начните общение.
                            </p>
                            <div class="actions">
                                <a class="button primary" href="/friends">Завести друзей</a>
                                <a class="button ghost" href="/profile">Заполнить анкету</a>
                            </div>
                        </div>
                    </section>
                    """;
        }

        StringBuilder cards = new StringBuilder();
        for (User friend : friends) {
            Profile profile = friend.profile();
            String meta = List.of(profile.city(), profile.goal()).stream()
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("друг в WFL");
            cards.append("""
                    <article class="chat-card">
                        <div class="friend-avatar">%s</div>
                        <div>
                            <h2>%s</h2>
                            <p>%s</p>
                        </div>
                        <button class="button ghost" type="button">Открыть чат</button>
                    </article>
                    """.formatted(friendInitial(friend), escapeHtml(friend.name()), escapeHtml(meta)));
        }

        return """
                <section class="chat-home">
                    <div class="chat-hero compact">
                        <p class="eyebrow">Home</p>
                        <h1>Чаты</h1>
                        <p>Ваши друзья уже рядом. Выберите человека, чтобы перейти к диалогу.</p>
                    </div>
                    <div class="chat-list">%s</div>
                </section>
                """.formatted(cards);
    }

    private String friendsPage(User user, String message, boolean success) {
        String notice = message == null ? "" : "<p class=\"" + (success ? "success" : "error") + "\">" + escapeHtml(message) + "</p>";
        List<User> friends = userStore.friendsOf(user);
        String friendList = friends.isEmpty()
                ? """
                <div class="empty-friends">
                    <strong>Друзей пока нет</strong>
                    <span>Отправьте свой код или введите чужой, чтобы добавить первого друга.</span>
                </div>
                """
                : friendList(friends);

        return """
                <section class="friends-page">
                    <div class="friend-code-panel">
                        <div>
                            <p class="eyebrow">Мой код дружбы</p>
                            <h1>%s</h1>
                            <p>Покажите этот код человеку, которого хотите добавить в друзья.</p>
                        </div>
                        <form class="form add-friend-form" method="post" action="/friends/add">
                            <label>Код друга
                                <input name="friendCode" autocomplete="off" inputmode="text" placeholder="Например, WFL-A1B2C3D4" required>
                            </label>
                            <button class="button primary" type="submit">Добавить</button>
                        </form>
                    </div>
                    %s
                    <section class="friends-list-panel">
                        <div class="panel-head">
                            <div>
                                <p class="eyebrow">Список друзей</p>
                                <h2>Друзья</h2>
                            </div>
                            <a class="button ghost" href="/home">На Home</a>
                        </div>
                        %s
                    </section>
                </section>
                """.formatted(escapeHtml(friendCodeFor(user.email())), notice, friendList);
    }

    private String friendList(List<User> friends) {
        StringBuilder html = new StringBuilder("<div class=\"friend-list\">");
        for (User friend : friends) {
            html.append("""
                    <article class="friend-row">
                        <div class="friend-avatar">%s</div>
                        <div>
                            <strong>%s</strong>
                            <span>%s</span>
                        </div>
                    </article>
                    """.formatted(friendInitial(friend), escapeHtml(friend.name()), escapeHtml(friend.profile().city().isBlank() ? friend.email() : friend.profile().city())));
        }
        html.append("</div>");
        return html.toString();
    }

    private String friendInitial(User user) {
        String name = user.name().isBlank() ? user.email() : user.name();
        return escapeHtml(name.substring(0, 1).toUpperCase());
    }

    private String settingsMenu(Optional<User> user) {
        if (user.isEmpty()) {
            return "";
        }

        return """
                <details class="settings-menu">
                    <summary aria-label="Настройки">
                        %s
                    </summary>
                    <div class="settings-panel">
                        <strong>%s</strong>
                        <a href="/profile">Моя анкета</a>
                        <form method="post" action="/logout">
                            <button type="submit">Выйти</button>
                        </form>
                    </div>
                </details>
                """.formatted(settingsIcon(), escapeHtml(user.get().name()));
    }

    private String settingsIcon() {
        return """
                <svg class="gear-icon" viewBox="0 0 32 32" role="img" aria-hidden="true" focusable="false">
                    <rect x="12" y="2" width="8" height="4" fill="#4ff7ff"/>
                    <rect x="12" y="26" width="8" height="4" fill="#8f6bff"/>
                    <rect x="2" y="12" width="4" height="8" fill="#4ff7ff"/>
                    <rect x="26" y="12" width="4" height="8" fill="#ff4fd8"/>
                    <rect x="6" y="6" width="6" height="4" fill="#9ffcff"/>
                    <rect x="20" y="6" width="6" height="4" fill="#f9ecff"/>
                    <rect x="6" y="22" width="6" height="4" fill="#bfff46"/>
                    <rect x="20" y="22" width="6" height="4" fill="#ff4fd8"/>
                    <rect x="8" y="10" width="16" height="12" fill="#f5f7ff"/>
                    <rect x="10" y="8" width="12" height="16" fill="#f5f7ff"/>
                    <rect x="12" y="12" width="8" height="8" fill="#151722"/>
                    <rect x="14" y="14" width="4" height="4" fill="#030307"/>
                    <rect x="8" y="10" width="4" height="4" fill="#4ff7ff" opacity="0.78"/>
                    <rect x="20" y="18" width="4" height="4" fill="#ff4fd8" opacity="0.82"/>
                </svg>
                """;
    }

    private String swipeMode() {
        return """
                <section class="mode-stage swipe-stage">
                    <article class="person-card">
                        <div class="person-meta">
                            <h1>Алина, 26</h1>
                            <p>Кофе, ночные прогулки, indie movies</p>
                            <div class="tag-row">
                                <span>Москва</span>
                                <span>отношения</span>
                                <span>музыка</span>
                            </div>
                        </div>
                    </article>
                    <div class="swipe-actions">
                        <button class="choice-button nope" type="button">Влево</button>
                        <button class="choice-button like" type="button">Вправо</button>
                    </div>
                </section>
                """;
    }

    private String twoVTwoMode() {
        return """
                <section class="mode-stage team-stage">
                    <div class="team-header">
                        <h1>2v2</h1>
                        <p>Сначала выберите друга: ваши анкеты будут показываться другим людям совместно, как команда.</p>
                    </div>
                    <div class="friend-setup">
                        <section class="friend-picker">
                            <h2>Выбор друга</h2>
                            <button class="friend-option selected" type="button">
                                <span>И</span>
                                <strong>Илья</strong>
                                <small>готов к 2v2</small>
                            </button>
                            <button class="friend-option" type="button">
                                <span>Д</span>
                                <strong>Дима</strong>
                                <small>онлайн</small>
                            </button>
                            <button class="friend-option" type="button">
                                <span>А</span>
                                <strong>Артем</strong>
                                <small>пригласить</small>
                            </button>
                        </section>
                        <section class="team-preview">
                            <h2>Ваша совместная анкета</h2>
                            <div class="duo-photos"><span>В</span><span>И</span></div>
                            <p>Вы + Илья будете отображаться как одна команда для пар противоположного пола.</p>
                            <button class="button primary" type="button">Создать команду</button>
                        </section>
                    </div>
                    <div class="team-grid">
                        <article class="duo-card">
                            <div class="duo-photos"><span>М</span><span>С</span></div>
                            <h2>Маша и София</h2>
                            <p>Бранчи, настолки, новые места</p>
                            <button class="button primary" type="button">Выбрать пару</button>
                        </article>
                        <article class="duo-card">
                            <div class="duo-photos"><span>К</span><span>Л</span></div>
                            <h2>Кира и Лена</h2>
                            <p>Концерты, спорт, путешествия</p>
                            <button class="button ghost" type="button">Посмотреть</button>
                        </article>
                    </div>
                    <aside class="friend-panel">
                        <strong>Команда</strong>
                        <span>Вы + Илья</span>
                    </aside>
                </section>
                """;
    }

    private String dateIdeasMode() {
        return """
                <section class="mode-stage ideas-stage">
                    <div class="team-header">
                        <h1>date ideas</h1>
                        <p>Вы заходите сюда с партнером, смотрите идеи для свиданий и вместе ставите лайк или дизлайк.</p>
                    </div>
                    <div class="partner-session">
                        <div>
                            <h2>Добавить партнера</h2>
                            <p>Отправьте партнеру ссылку или код, чтобы он подключился к этой сессии выбора свиданий.</p>
                        </div>
                        <div class="invite-box">
                            <span>Код сессии</span>
                            <strong>DATE-482</strong>
                            <button class="button primary" type="button">Скопировать ссылку</button>
                        </div>
                        <div class="session-status">
                            <strong>Ожидаем партнера</strong>
                            <span>После подключения лайки будут считаться совместно.</span>
                        </div>
                    </div>
                    <div class="ideas-grid">
                        <article class="idea-card">
                            <h2>Кино под открытым небом</h2>
                            <p>Плед, горячий чай и фильм вечером.</p>
                            <div class="vote-row">
                                <button class="choice-button nope" type="button">Дизлайк</button>
                                <button class="choice-button like" type="button">Лайк</button>
                            </div>
                        </article>
                        <article class="idea-card">
                            <h2>Случайная станция метро</h2>
                            <p>Выбираете станцию и находите там лучшее место за час.</p>
                            <div class="vote-row">
                                <button class="choice-button nope" type="button">Дизлайк</button>
                                <button class="choice-button like" type="button">Лайк</button>
                            </div>
                        </article>
                        <article class="idea-card">
                            <h2>Кулинарный баттл</h2>
                            <p>Каждый готовит блюдо из трех случайных ингредиентов.</p>
                            <div class="vote-row">
                                <button class="choice-button nope" type="button">Дизлайк</button>
                                <button class="choice-button like" type="button">Лайк</button>
                            </div>
                        </article>
                    </div>
                </section>
                """;
    }

    private boolean isDatingMode(String mode) {
        return "swipe".equals(mode) || "2v2".equals(mode) || "date-ideas".equals(mode);
    }

    private String modeMenu(Optional<User> user, String mode) {
        if (user.isEmpty()) {
            return "";
        }

        if (isDatingMode(mode)) {
            return """
                    <nav class="mode-menu" aria-label="Навигация">
                        <a class="home-link" href="/home">На главный Home</a>
                        %s
                    </nav>
                    """.formatted(helpPopup());
        }

        return """
                <nav class="mode-menu" aria-label="Режимы">
                    <a href="/friends">Друзья</a>
                    <a href="/home?mode=swipe">swipe</a>
                    <a href="/home?mode=2v2">2v2</a>
                    <a href="/home?mode=date-ideas">date ideas</a>
                    %s
                </nav>
                """.formatted(helpPopup());
    }

    private String helpPopup() {
        return """
                <details class="help-popup">
                    <summary aria-label="Информация о сайте">?</summary>
                    <div class="help-window">
                        <h2>О сайте</h2>
                        <p>WFL помогает знакомиться через личную анкету, друзей, игровые режимы и совместный выбор идей для свиданий.</p>
                        <h3>swipe</h3>
                        <p>Классический режим: смотрите карточку человека и выбираете влево или вправо.</p>
                        <h3>2v2</h3>
                        <p>Выбираете друга, объединяете анкеты и знакомитесь с парой людей противоположного пола.</p>
                        <h3>date ideas</h3>
                        <p>Подключаете партнера к сессии, смотрите идеи свиданий и вместе ставите лайк или дизлайк.</p>
                    </div>
                </details>
                """;
    }

    private String page(String title, String body) {
        return page(title, body, Optional.empty());
    }

    private String page(String title, String body, User user) {
        return page(title, body, Optional.of(user));
    }

    private String page(String title, String body, Optional<User> user) {
        return page(title, body, user, "");
    }

    private String page(String title, String body, Optional<User> user, String mode) {
        return """
                <!doctype html>
                <html lang="ru">
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <title>%s</title>
                    <style>
                        :root {
                            color-scheme: dark;
                            --ink: #f5f7ff;
                            --muted: #a4acc2;
                            --line: rgba(255, 255, 255, 0.14);
                            --paper: rgba(255, 255, 255, 0.075);
                            --bg: #030307;
                            --accent: #4ff7ff;
                            --accent-hot: #ff4fd8;
                            --accent-lime: #bfff46;
                            --green: #5ff0b1;
                            --red: #ff7a8a;
                            --shadow: 0 28px 90px rgba(0, 0, 0, 0.5);
                        }
                        * { box-sizing: border-box; }
                        html { scroll-behavior: smooth; }
                        body {
                            margin: 0;
                            font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                            color: var(--ink);
                            background:
                                radial-gradient(circle at 18%% 12%%, rgba(79, 247, 255, 0.17), transparent 28rem),
                                radial-gradient(circle at 80%% 2%%, rgba(255, 79, 216, 0.16), transparent 30rem),
                                linear-gradient(135deg, #020205 0%%, #080912 50%%, #030307 100%%);
                            min-width: 320px;
                            overflow-x: hidden;
                        }
                        body::before {
                            position: fixed;
                            inset: 0;
                            z-index: -2;
                            pointer-events: none;
                            content: "";
                            background-image:
                                linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px),
                                linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
                            background-size: 72px 72px;
                            mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.9), transparent 78%%);
                        }
                        body::after {
                            position: fixed;
                            inset: 0;
                            z-index: -1;
                            pointer-events: none;
                            content: "";
                            opacity: 0.42;
                            background-image: url("data:image/svg+xml,%%3Csvg viewBox='0 0 180 180' xmlns='http://www.w3.org/2000/svg'%%3E%%3Cfilter id='n'%%3E%%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='3' stitchTiles='stitch'/%%3E%%3C/filter%%3E%%3Crect width='180' height='180' filter='url(%%23n)' opacity='.34'/%%3E%%3C/svg%%3E");
                            mix-blend-mode: soft-light;
                        }
                        a { color: var(--accent); }
                        .topbar {
                            width: min(1180px, calc(100%% - 40px));
                            margin: 18px auto 0;
                            display: grid;
                            grid-template-columns: auto 1fr auto;
                            gap: 16px;
                            min-height: 104px;
                            padding: 12px 18px 12px 20px;
                            border: 1px solid var(--line);
                            border-radius: 28px;
                            background: linear-gradient(120deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.045));
                            box-shadow: 0 20px 70px rgba(0, 0, 0, 0.36);
                            backdrop-filter: blur(26px) saturate(135%%);
                            position: sticky;
                            top: 18px;
                            z-index: 10;
                        }
                        .brand {
                            color: var(--ink);
                            display: inline-flex;
                            align-items: center;
                            gap: 10px;
                            font-weight: 800;
                            text-decoration: none;
                        }
                        .brand.brand-icon-only {
                            gap: 0;
                        }
                        .mode-menu {
                            grid-column: 1 / -1;
                            grid-row: 2;
                            display: flex;
                            align-items: center;
                            gap: 8px;
                            flex-wrap: wrap;
                        }
                        .mode-menu a,
                        .help-popup summary {
                            min-height: 38px;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            padding: 0 14px;
                            border: 1px solid rgba(255, 255, 255, 0.14);
                            border-radius: 999px;
                            color: var(--ink);
                            background: rgba(255, 255, 255, 0.065);
                            font-size: 13px;
                            font-weight: 900;
                            text-decoration: none;
                        }
                        .help-popup {
                            position: relative;
                        }
                        .help-popup summary {
                            width: 38px;
                            padding: 0;
                            cursor: pointer;
                            list-style: none;
                        }
                        .help-popup summary::-webkit-details-marker {
                            display: none;
                        }
                        .mode-menu a:hover,
                        .help-popup summary:hover {
                            border-color: rgba(79, 247, 255, 0.42);
                            background: rgba(79, 247, 255, 0.1);
                        }
                        .help-window {
                            position: absolute;
                            top: calc(100%% + 12px);
                            left: 0;
                            z-index: 20;
                            width: min(420px, calc(100vw - 44px));
                            display: grid;
                            gap: 10px;
                            padding: 20px;
                            border: 1px solid var(--line);
                            border-radius: 22px;
                            background: rgba(14, 15, 24, 0.97);
                            box-shadow: var(--shadow);
                            backdrop-filter: blur(24px);
                        }
                        .help-window h2,
                        .help-window h3,
                        .help-window p {
                            margin: 0;
                        }
                        .help-window h2 {
                            font-size: 24px;
                        }
                        .help-window h3 {
                            margin-top: 6px;
                            color: var(--accent);
                            font-size: 15px;
                            text-transform: uppercase;
                        }
                        .help-window p {
                            color: var(--muted);
                            line-height: 1.45;
                        }
                        .brand::before {
                            width: 34px;
                            height: 34px;
                            border-radius: 12px;
                            content: "";
                            background:
                                linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0.05)),
                                conic-gradient(from 150deg, var(--accent), var(--accent-hot), var(--accent-lime), var(--accent));
                            box-shadow: 0 0 30px rgba(79, 247, 255, 0.38), inset 0 1px 0 rgba(255, 255, 255, 0.5);
                        }
                        .topbar nav {
                            display: flex;
                            align-items: center;
                            gap: 22px;
                            flex-wrap: wrap;
                        }
                        .topbar nav a {
                            color: var(--muted);
                            font-size: 14px;
                            font-weight: 700;
                            text-decoration: none;
                            transition: color 180ms ease;
                        }
                        .topbar nav a:hover {
                            color: var(--ink);
                        }
                        .settings-menu {
                            grid-column: 3;
                            grid-row: 1;
                            align-self: start;
                            position: relative;
                            margin-left: auto;
                        }
                        .settings-menu summary,
                        .settings-link {
                            width: 56px;
                            height: 56px;
                            display: grid;
                            place-items: center;
                            border: 1px solid rgba(255, 255, 255, 0.18);
                            border-radius: 16px;
                            background:
                                linear-gradient(145deg, rgba(255, 255, 255, 0.14), rgba(255, 255, 255, 0.045)),
                                rgba(255, 255, 255, 0.075);
                            cursor: pointer;
                            list-style: none;
                            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16), 0 0 28px rgba(79, 247, 255, 0.14);
                            transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
                            text-decoration: none;
                        }
                        .settings-menu summary:hover,
                        .settings-link:hover {
                            transform: translateY(-2px);
                            border-color: rgba(79, 247, 255, 0.48);
                            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.2), 0 0 34px rgba(255, 79, 216, 0.24);
                        }
                        .settings-menu summary::-webkit-details-marker {
                            display: none;
                        }
                        .gear-icon {
                            display: block;
                            width: 38px;
                            height: 38px;
                            overflow: visible;
                            image-rendering: pixelated;
                            shape-rendering: crispEdges;
                            filter: drop-shadow(0 0 10px rgba(79, 247, 255, 0.32)) drop-shadow(0 0 8px rgba(255, 79, 216, 0.18));
                        }
                        .settings-panel {
                            position: absolute;
                            top: calc(100%% + 12px);
                            right: 0;
                            width: 220px;
                            display: grid;
                            gap: 8px;
                            padding: 14px;
                            border: 1px solid var(--line);
                            border-radius: 20px;
                            background: rgba(14, 15, 24, 0.96);
                            box-shadow: var(--shadow);
                            backdrop-filter: blur(24px);
                        }
                        .settings-panel strong {
                            padding: 4px 6px 8px;
                            overflow-wrap: anywhere;
                        }
                        .settings-panel a,
                        .settings-panel button {
                            width: 100%%;
                            min-height: 42px;
                            display: flex;
                            align-items: center;
                            padding: 0 12px;
                            border: 0;
                            border-radius: 12px;
                            color: var(--ink);
                            background: rgba(255, 255, 255, 0.07);
                            font: inherit;
                            font-weight: 800;
                            text-align: left;
                            text-decoration: none;
                            cursor: pointer;
                        }
                        main {
                            width: min(1180px, calc(100%% - 40px));
                            margin: 0 auto 82px;
                        }
                        .auth-screen {
                            min-height: calc(100vh - 112px);
                            display: grid;
                            place-items: center;
                            padding: 60px 0;
                        }
                        .auth-window {
                            width: min(460px, 100%%);
                            border: 1px solid var(--line);
                            border-radius: 32px;
                            background:
                                linear-gradient(130deg, rgba(255, 255, 255, 0.16), transparent 30%%),
                                rgba(255, 255, 255, 0.07);
                            box-shadow: var(--shadow), inset 0 1px 0 rgba(255, 255, 255, 0.16);
                            backdrop-filter: blur(24px) saturate(135%%);
                            padding: 18px;
                        }
                        .hero {
                            min-height: calc(100vh - 92px);
                            display: grid;
                            grid-template-columns: minmax(0, 1fr) minmax(360px, 0.82fr);
                            gap: 44px;
                            align-items: center;
                            padding: 74px 0 58px;
                        }
                        .home-empty {
                            min-height: calc(100vh - 112px);
                            display: grid;
                            align-content: center;
                            place-items: center;
                            gap: 30px;
                            padding: 44px 0;
                        }
                        .home-service-info {
                            width: min(780px, 100%%);
                            display: grid;
                            gap: 18px;
                            padding: 30px;
                            border: 1px solid var(--line);
                            border-radius: 30px;
                            background:
                                linear-gradient(130deg, rgba(255, 255, 255, 0.13), transparent 34%%),
                                rgba(255, 255, 255, 0.065);
                            box-shadow: var(--shadow), inset 0 1px 0 rgba(255, 255, 255, 0.14);
                            backdrop-filter: blur(24px) saturate(135%%);
                            text-align: left;
                        }
                        .home-service-info h1 {
                            max-width: 650px;
                            margin: 0;
                            font-size: clamp(34px, 5vw, 62px);
                            line-height: 0.98;
                            font-weight: 900;
                            letter-spacing: 0;
                        }
                        .home-service-info p {
                            max-width: 680px;
                            margin: 0;
                            color: var(--muted);
                            font-size: 17px;
                            line-height: 1.6;
                        }
                        .home-info-grid {
                            display: grid;
                            grid-template-columns: repeat(3, minmax(0, 1fr));
                            gap: 12px;
                            margin-top: 4px;
                        }
                        .home-info-grid div {
                            min-height: 112px;
                            padding: 18px;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 22px;
                            background: rgba(255, 255, 255, 0.055);
                        }
                        .home-info-grid strong,
                        .home-info-grid span {
                            display: block;
                        }
                        .home-info-grid strong {
                            font-size: 20px;
                            font-weight: 900;
                        }
                        .home-info-grid span {
                            margin-top: 10px;
                            color: var(--muted);
                            font-size: 14px;
                            line-height: 1.45;
                        }
                        .chat-home,
                        .friends-page {
                            min-height: calc(100vh - 112px);
                            display: grid;
                            align-content: start;
                            gap: 18px;
                            padding: 44px 0;
                        }
                        .empty-chat-home {
                            align-content: center;
                        }
                        .chat-hero,
                        .friend-code-panel,
                        .friends-list-panel {
                            border: 1px solid var(--line);
                            border-radius: 28px;
                            background: var(--paper);
                            box-shadow: var(--shadow), inset 0 1px 0 rgba(255, 255, 255, 0.14);
                            backdrop-filter: blur(24px) saturate(135%%);
                            padding: 30px;
                        }
                        .chat-hero h1,
                        .friend-code-panel h1 {
                            max-width: 720px;
                            margin: 0 0 14px;
                            font-size: clamp(38px, 6vw, 76px);
                            line-height: 0.95;
                            letter-spacing: 0;
                        }
                        .chat-hero p,
                        .friend-code-panel p,
                        .empty-friends span,
                        .friend-row span,
                        .chat-card p {
                            margin: 0;
                            color: var(--muted);
                            line-height: 1.55;
                        }
                        .chat-hero p {
                            max-width: 620px;
                            margin-bottom: 20px;
                            font-size: 18px;
                        }
                        .chat-hero.compact h1 {
                            font-size: clamp(34px, 5vw, 58px);
                        }
                        .chat-list,
                        .friend-list {
                            display: grid;
                            gap: 12px;
                        }
                        .chat-card,
                        .friend-row {
                            display: grid;
                            grid-template-columns: 56px minmax(0, 1fr) auto;
                            align-items: center;
                            gap: 14px;
                            min-height: 88px;
                            padding: 16px;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 22px;
                            background: rgba(255, 255, 255, 0.055);
                        }
                        .friend-row {
                            grid-template-columns: 52px minmax(0, 1fr);
                        }
                        .chat-card h2 {
                            margin: 0 0 4px;
                            font-size: 22px;
                        }
                        .friend-avatar {
                            width: 52px;
                            height: 52px;
                            display: grid;
                            place-items: center;
                            border-radius: 18px;
                            color: #020205;
                            background: linear-gradient(135deg, var(--accent), var(--accent-lime));
                            font-weight: 900;
                        }
                        .friend-code-panel {
                            display: grid;
                            grid-template-columns: minmax(0, 1fr) minmax(280px, 420px);
                            gap: 24px;
                            align-items: end;
                        }
                        .friend-code-panel h1 {
                            word-break: break-word;
                            font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                            font-size: clamp(34px, 6vw, 62px);
                        }
                        .add-friend-form {
                            padding: 18px;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 22px;
                            background: rgba(3, 3, 7, 0.28);
                        }
                        .empty-friends {
                            display: grid;
                            gap: 6px;
                            padding: 18px;
                            border: 1px dashed rgba(255, 255, 255, 0.22);
                            border-radius: 22px;
                            background: rgba(255, 255, 255, 0.035);
                        }
                        .mode-stage {
                            min-height: calc(100vh - 150px);
                            display: grid;
                            align-content: center;
                            gap: 24px;
                            padding: 44px 0;
                        }
                        .swipe-stage {
                            justify-items: center;
                        }
                        .person-card {
                            width: min(420px, 100%%);
                            min-height: 560px;
                            display: grid;
                            align-content: end;
                            overflow: hidden;
                            position: relative;
                            isolation: isolate;
                            border: 1px solid rgba(255, 255, 255, 0.2);
                            border-radius: 34px;
                            background: #080912;
                            box-shadow:
                                0 0 0 1px rgba(79, 247, 255, 0.32),
                                0 0 0 2px rgba(255, 79, 216, 0.18),
                                0 0 42px rgba(79, 247, 255, 0.26),
                                0 0 62px rgba(255, 79, 216, 0.18),
                                var(--shadow);
                        }
                        .person-card::before,
                        .duo-card::before,
                        .team-preview::before {
                            position: absolute;
                            inset: 0;
                            z-index: -2;
                            content: "";
                            background-image: url("/assets/placeholders/profile-card-placeholder.png");
                            background-size: cover;
                            background-position: center top;
                            transform: scale(1.22);
                            transform-origin: center top;
                        }
                        .person-card::after,
                        .duo-card::after,
                        .team-preview::after {
                            position: absolute;
                            inset: 0;
                            z-index: -1;
                            content: "";
                            background:
                                linear-gradient(to top, rgba(3, 3, 7, 0.96), rgba(3, 3, 7, 0.58) 26%%, rgba(3, 3, 7, 0.06) 62%%),
                                linear-gradient(145deg, rgba(79, 247, 255, 0.08), rgba(255, 79, 216, 0.1));
                        }
                        .person-meta {
                            position: relative;
                            z-index: 1;
                            padding: 28px;
                        }
                        .person-meta h1,
                        .team-header h1 {
                            margin: 0 0 10px;
                            font-size: clamp(36px, 6vw, 64px);
                            line-height: 0.96;
                        }
                        .person-meta p,
                        .team-header p,
                        .duo-card p,
                        .idea-card p {
                            margin: 0;
                            color: var(--muted);
                            line-height: 1.55;
                        }
                        .tag-row,
                        .vote-row,
                        .swipe-actions {
                            display: flex;
                            gap: 10px;
                            flex-wrap: wrap;
                            align-items: center;
                            justify-content: center;
                        }
                        .tag-row {
                            justify-content: flex-start;
                            margin-top: 18px;
                        }
                        .tag-row span {
                            padding: 8px 11px;
                            border: 1px solid rgba(255, 255, 255, 0.14);
                            border-radius: 999px;
                            background: rgba(255, 255, 255, 0.08);
                            font-size: 13px;
                            font-weight: 800;
                        }
                        .choice-button {
                            min-width: 118px;
                            min-height: 48px;
                            border: 1px solid rgba(255, 255, 255, 0.16);
                            border-radius: 999px;
                            color: var(--ink);
                            background: rgba(255, 255, 255, 0.08);
                            cursor: pointer;
                            font: inherit;
                            font-weight: 900;
                        }
                        .choice-button.like {
                            color: #030307;
                            background: var(--accent-lime);
                        }
                        .choice-button.nope {
                            background: rgba(255, 79, 216, 0.2);
                        }
                        .team-stage,
                        .ideas-stage {
                            align-content: start;
                        }
                        .team-header {
                            max-width: 760px;
                            padding-top: 24px;
                        }
                        .team-grid,
                        .ideas-grid,
                        .friend-setup,
                        .partner-session {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
                            gap: 16px;
                        }
                        .duo-card,
                        .idea-card,
                        .friend-panel,
                        .friend-picker,
                        .team-preview,
                        .partner-session {
                            border: 1px solid var(--line);
                            border-radius: 28px;
                            background: rgba(255, 255, 255, 0.075);
                            box-shadow: var(--shadow), inset 0 1px 0 rgba(255, 255, 255, 0.14);
                            backdrop-filter: blur(24px);
                            padding: 24px;
                        }
                        .friend-picker,
                        .team-preview {
                            display: grid;
                            gap: 14px;
                        }
                        .friend-picker h2,
                        .team-preview h2,
                        .partner-session h2 {
                            margin: 0;
                            font-size: 24px;
                        }
                        .friend-option {
                            min-height: 72px;
                            display: grid;
                            grid-template-columns: 48px 1fr;
                            column-gap: 12px;
                            align-items: center;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 18px;
                            color: var(--ink);
                            background: rgba(255, 255, 255, 0.055);
                            cursor: pointer;
                            font: inherit;
                            text-align: left;
                        }
                        .friend-option.selected {
                            border-color: rgba(79, 247, 255, 0.5);
                            background: rgba(79, 247, 255, 0.12);
                        }
                        .friend-option span {
                            grid-row: span 2;
                            width: 44px;
                            height: 44px;
                            display: grid;
                            place-items: center;
                            border-radius: 50%%;
                            color: #030307;
                            background: var(--accent);
                            font-weight: 900;
                        }
                        .friend-option strong,
                        .friend-option small {
                            display: block;
                        }
                        .friend-option small {
                            color: var(--muted);
                            font-weight: 800;
                        }
                        .duo-card {
                            display: grid;
                            align-content: end;
                            gap: 14px;
                            min-height: 360px;
                            overflow: hidden;
                            position: relative;
                            isolation: isolate;
                            box-shadow:
                                0 0 0 1px rgba(79, 247, 255, 0.26),
                                0 0 34px rgba(255, 79, 216, 0.17),
                                var(--shadow),
                                inset 0 1px 0 rgba(255, 255, 255, 0.14);
                        }
                        .team-preview {
                            min-height: 360px;
                            align-content: end;
                            overflow: hidden;
                            position: relative;
                            isolation: isolate;
                            box-shadow:
                                0 0 0 1px rgba(79, 247, 255, 0.26),
                                0 0 34px rgba(255, 79, 216, 0.17),
                                var(--shadow),
                                inset 0 1px 0 rgba(255, 255, 255, 0.14);
                        }
                        .duo-card > *,
                        .team-preview > * {
                            position: relative;
                            z-index: 1;
                        }
                        .duo-card h2,
                        .idea-card h2 {
                            margin: 0;
                            font-size: 26px;
                        }
                        .duo-card h2,
                        .team-preview h2 {
                            text-shadow: 0 3px 18px rgba(0, 0, 0, 0.54);
                        }
                        .duo-photos {
                            display: flex;
                            margin-bottom: 8px;
                        }
                        .duo-card .duo-photos,
                        .team-preview .duo-photos {
                            display: none;
                        }
                        .duo-photos span {
                            width: 74px;
                            height: 74px;
                            display: grid;
                            place-items: center;
                            border: 3px solid rgba(3, 3, 7, 0.8);
                            border-radius: 50%%;
                            color: #030307;
                            background: linear-gradient(135deg, var(--accent), var(--accent-hot));
                            font-size: 28px;
                            font-weight: 900;
                        }
                        .duo-photos span + span {
                            margin-left: -16px;
                            background: linear-gradient(135deg, var(--accent-lime), #f5f7ff);
                        }
                        .friend-panel {
                            display: flex;
                            align-items: center;
                            justify-content: space-between;
                            gap: 14px;
                        }
                        .friend-panel strong,
                        .friend-panel span {
                            font-weight: 900;
                        }
                        .idea-card {
                            display: grid;
                            gap: 18px;
                        }
                        .idea-card .vote-row {
                            justify-content: flex-start;
                        }
                        .partner-session {
                            align-items: center;
                        }
                        .partner-session p,
                        .session-status span,
                        .invite-box span {
                            margin: 0;
                            color: var(--muted);
                            line-height: 1.5;
                        }
                        .invite-box,
                        .session-status {
                            display: grid;
                            gap: 8px;
                            padding: 16px;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 18px;
                            background: rgba(3, 3, 7, 0.28);
                        }
                        .invite-box strong {
                            font-size: 28px;
                            letter-spacing: 0;
                        }
                        .hero-visual {
                            min-height: 430px;
                            border: 1px solid var(--line);
                            border-radius: 34px;
                            background:
                                linear-gradient(130deg, rgba(255, 255, 255, 0.22), transparent 28%%),
                                radial-gradient(circle at 18%% 12%%, rgba(79, 247, 255, 0.22), transparent 22rem),
                                radial-gradient(circle at 88%% 88%%, rgba(255, 79, 216, 0.18), transparent 18rem),
                                linear-gradient(145deg, rgba(255, 255, 255, 0.13), rgba(255, 255, 255, 0.045));
                            box-shadow: var(--shadow), 0 0 72px rgba(79, 247, 255, 0.14), inset 0 1px 0 rgba(255, 255, 255, 0.18);
                            backdrop-filter: blur(24px) saturate(135%%);
                            padding: 26px;
                            overflow: hidden;
                            position: relative;
                        }
                        .visual-topline {
                            display: flex;
                            align-items: center;
                            gap: 7px;
                            color: rgba(245, 247, 255, 0.68);
                            font-size: 13px;
                            font-weight: 800;
                        }
                        .visual-topline span {
                            width: 9px;
                            height: 9px;
                            border-radius: 50%%;
                            background: var(--accent-hot);
                        }
                        .visual-topline span:nth-child(2) { background: var(--accent-lime); }
                        .visual-topline span:nth-child(3) { background: var(--accent); }
                        .visual-topline b {
                            margin-left: auto;
                        }
                        .visual-main {
                            margin-top: 62px;
                        }
                        .visual-main small,
                        .visual-grid small {
                            display: block;
                            color: var(--muted);
                            font-size: 13px;
                            font-weight: 800;
                        }
                        .visual-main strong {
                            display: block;
                            margin-top: 12px;
                            font-size: clamp(68px, 9vw, 104px);
                            line-height: 0.86;
                            font-weight: 900;
                        }
                        .visual-grid {
                            display: grid;
                            grid-template-columns: repeat(3, minmax(0, 1fr));
                            gap: 12px;
                            margin-top: 36px;
                        }
                        .visual-grid div {
                            min-height: 104px;
                            padding: 16px;
                            border: 1px solid rgba(255, 255, 255, 0.11);
                            border-radius: 22px;
                            background: rgba(255, 255, 255, 0.055);
                        }
                        .visual-grid b {
                            display: block;
                            margin-top: 18px;
                            font-size: 24px;
                        }
                        .visual-progress {
                            height: 12px;
                            margin-top: 38px;
                            border-radius: 999px;
                            background: rgba(255, 255, 255, 0.09);
                            overflow: hidden;
                        }
                        .visual-progress span {
                            display: block;
                            width: 78%%;
                            height: 100%%;
                            border-radius: inherit;
                            background: linear-gradient(90deg, var(--accent), var(--accent-hot), var(--accent-lime));
                            box-shadow: 0 0 26px rgba(255, 79, 216, 0.38);
                        }
                        .hero h1, .panel h1 {
                            margin: 0 0 14px;
                            font-size: clamp(42px, 7vw, 86px);
                            line-height: 0.94;
                            letter-spacing: 0;
                            font-weight: 900;
                        }
                        .hero p {
                            max-width: 620px;
                            margin: 0 0 24px;
                            color: var(--muted);
                            font-size: 20px;
                            line-height: 1.6;
                        }
                        .eyebrow {
                            margin: 0 0 10px;
                            color: var(--accent);
                            font-size: 13px;
                            font-weight: 800;
                            letter-spacing: 0;
                            text-transform: uppercase;
                        }
                        .actions {
                            display: flex;
                            align-items: center;
                            gap: 12px;
                            flex-wrap: wrap;
                        }
                        .button {
                            position: relative;
                            isolation: isolate;
                            display: inline-flex;
                            align-items: center;
                            justify-content: center;
                            min-height: 48px;
                            padding: 0 22px;
                            border: 1px solid rgba(255, 255, 255, 0.18);
                            border-radius: 999px;
                            background: rgba(255, 255, 255, 0.075);
                            color: var(--ink);
                            cursor: pointer;
                            font: inherit;
                            font-size: 14px;
                            font-weight: 800;
                            text-decoration: none;
                            overflow: hidden;
                            transition: transform 200ms ease, border-color 200ms ease, box-shadow 200ms ease;
                        }
                        .button:hover {
                            transform: translateY(-2px);
                        }
                        .button.primary {
                            border-color: rgba(255, 255, 255, 0.28);
                            background: linear-gradient(110deg, var(--accent), #f5fbff 34%%, var(--accent-hot) 67%%, var(--accent-lime));
                            background-size: 220%% 100%%;
                            color: #020205;
                            box-shadow: 0 0 42px rgba(79, 247, 255, 0.3), 0 18px 50px rgba(255, 79, 216, 0.18);
                            animation: shimmer 5s linear infinite;
                        }
                        .button.ghost {
                            background: rgba(255, 255, 255, 0.065);
                            box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16);
                            backdrop-filter: blur(18px);
                        }
                        .panel {
                            margin-top: 22px;
                            padding: 30px;
                            border: 1px solid var(--line);
                            border-radius: 28px;
                            background: var(--paper);
                            box-shadow: var(--shadow), inset 0 1px 0 rgba(255, 255, 255, 0.14);
                            backdrop-filter: blur(24px) saturate(135%%);
                        }
                        .panel.narrow {
                            max-width: 520px;
                            margin-inline: auto;
                        }
                        .auth-window .panel {
                            margin-top: 0;
                            box-shadow: none;
                            background: rgba(255, 255, 255, 0.055);
                        }
                        .auth-window .panel.narrow {
                            max-width: none;
                            margin: 0;
                        }
                        .auth-window .panel h1 {
                            font-size: 28px;
                            line-height: 1.08;
                        }
                        .auth-switch {
                            text-align: center;
                        }
                        .panel h2 {
                            margin: 0 0 16px;
                            font-size: 24px;
                        }
                        .panel-head {
                            display: flex;
                            align-items: flex-start;
                            justify-content: space-between;
                            gap: 16px;
                            margin-bottom: 20px;
                        }
                        .panel-head h1 {
                            font-size: 34px;
                            margin-bottom: 0;
                        }
                        .menu, .feature-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
                            gap: 12px;
                        }
                        .menu a, .feature-grid div {
                            min-height: 88px;
                            padding: 20px;
                            border: 1px solid rgba(255, 255, 255, 0.12);
                            border-radius: 22px;
                            color: var(--ink);
                            background: rgba(255, 255, 255, 0.055);
                            text-decoration: none;
                        }
                        .feature-grid strong, .feature-grid span {
                            display: block;
                        }
                        .feature-grid span {
                            margin-top: 6px;
                            color: var(--muted);
                            line-height: 1.45;
                        }
                        .form {
                            display: grid;
                            gap: 16px;
                        }
                        label {
                            display: grid;
                            gap: 7px;
                            color: rgba(245, 247, 255, 0.84);
                            font-weight: 700;
                        }
                        input, textarea, select {
                            width: 100%%;
                            border: 1px solid var(--line);
                            border-radius: 16px;
                            padding: 13px 14px;
                            color: var(--ink);
                            background: rgba(3, 3, 7, 0.48);
                            font: inherit;
                            font-weight: 500;
                            outline: none;
                            transition: border-color 180ms ease, box-shadow 180ms ease, background 180ms ease;
                        }
                        input:focus, textarea:focus, select:focus {
                            border-color: rgba(79, 247, 255, 0.58);
                            background: rgba(3, 3, 7, 0.62);
                            box-shadow: 0 0 0 4px rgba(79, 247, 255, 0.1);
                        }
                        textarea { resize: vertical; }
                        .muted {
                            margin: 0;
                            color: var(--muted);
                        }
                        .error, .success {
                            padding: 12px 14px;
                            border-radius: 16px;
                            font-weight: 700;
                        }
                        .error {
                            color: var(--red);
                            background: rgba(255, 122, 138, 0.12);
                        }
                        .success {
                            color: var(--green);
                            background: rgba(95, 240, 177, 0.12);
                        }
                        @keyframes shimmer {
                            0%% { background-position: 0%% 50%%; }
                            100%% { background-position: 220%% 50%%; }
                        }
                        @media (prefers-reduced-motion: reduce) {
                            *, *::before, *::after {
                                scroll-behavior: auto !important;
                                animation-duration: 0.001ms !important;
                                animation-iteration-count: 1 !important;
                                transition-duration: 0.001ms !important;
                            }
                        }
                        @media (max-width: 760px) {
                            .topbar {
                                align-items: flex-start;
                                grid-template-columns: auto 1fr auto;
                                width: min(100%% - 22px, 520px);
                                padding: 16px;
                                top: 10px;
                            }
                            .mode-menu {
                                width: 100%%;
                            }
                            main {
                                width: min(100%% - 22px, 520px);
                                margin-bottom: 48px;
                            }
                            .hero {
                                grid-template-columns: 1fr;
                                min-height: auto;
                                padding: 48px 0 34px;
                            }
                            .auth-screen {
                                min-height: auto;
                                padding: 40px 0;
                            }
                            .hero-visual {
                                min-height: 250px;
                                border-radius: 28px;
                            }
                            .home-info-grid {
                                grid-template-columns: 1fr;
                            }
                            .friend-code-panel,
                            .chat-card {
                                grid-template-columns: 1fr;
                            }
                            .chat-card {
                                justify-items: start;
                            }
                            .visual-main { margin-top: 38px; }
                            .visual-grid { grid-template-columns: 1fr; }
                            .hero, .panel {
                                padding: 22px;
                            }
                            .panel-head {
                                display: grid;
                            }
                        }
                    </style>
                </head>
                <body>
                    <header class="topbar">
                        <a class="brand %s" href="%s" aria-label="WFL - who'll find love">%s</a>
                        %s
                        %s
                    </header>
                    <main>%s</main>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                "",
                user.map(ignored -> "/home").orElse("/"),
                "WFL",
                modeMenu(user, mode),
                settingsMenu(user),
                body
        );
    }

    private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> fields = new HashMap<>();
        if (body.isBlank()) {
            return fields;
        }

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length == 2 ? urlDecode(parts[1]) : "";
            fields.put(key, value);
        }
        return fields;
    }

    private Optional<String> queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            if (name.equals(key)) {
                return Optional.of(parts.length == 2 ? urlDecode(parts[1]) : "");
            }
        }
        return Optional.empty();
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeEmail(String value) {
        return clean(value).toLowerCase();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private Optional<String> readCookie(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getOrDefault("Cookie", List.of()).stream()
                .flatMap(header -> List.of(header.split(";")).stream())
                .map(String::trim)
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(name.length() + 1))
                .findFirst();
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private void sendHtml(HttpExchange exchange, String html) throws IOException {
        sendHtml(exchange, 200, html);
    }

    private void sendHtml(HttpExchange exchange, int statusCode, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendFile(HttpExchange exchange, Path file, String contentType) throws IOException {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            sendHtml(exchange, 404, page("Не найдено", "<section class=\"panel\"><h1>Файл не найден</h1><a href=\"/home\">На главную</a></section>"));
            return;
        }

        byte[] bytes = Files.readAllBytes(file);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "public, max-age=3600");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static String friendCodeFor(String email) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalizeEmail(email).getBytes(StandardCharsets.UTF_8));
            StringBuilder code = new StringBuilder("WFL-");
            for (int index = 0; index < 4; index++) {
                code.append(String.format("%02X", digest[index]));
            }
            return code.toString();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot create friend code", error);
        }
    }

    private static String normalizeFriendCode(String value) {
        return clean(value).toUpperCase().replace(" ", "");
    }

    enum FriendAddResult {
        ADDED,
        ALREADY_FRIENDS,
        SELF,
        NOT_FOUND
    }

    record User(String email, String name, String passwordHash, Profile profile, LocalDate createdAt, List<String> friendEmails) {
        User {
            friendEmails = List.copyOf(friendEmails);
        }

        User withFriend(String friendEmail) {
            Set<String> emails = new LinkedHashSet<>(friendEmails);
            emails.add(friendEmail);
            return new User(email, name, passwordHash, profile, createdAt, List.copyOf(emails));
        }
    }

    record Profile(String age, String city, String goal, String interests, String about) {
        static Profile empty() {
            return new Profile("", "", "Серьезные отношения", "", "");
        }
    }

    static class UserStore {
        private final Path file;
        private final Map<String, User> users = new ConcurrentHashMap<>();

        UserStore(Path file) {
            this.file = file;
        }

        void load() throws IOException {
            if (!Files.exists(file)) {
                return;
            }

            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                User user = decodeUser(line);
                users.put(user.email(), user);
            }
        }

        Optional<User> register(String email, String name, String passwordHash) {
            User user = new User(email, name, passwordHash, Profile.empty(), LocalDate.now(), List.of());
            User existing = users.putIfAbsent(email, user);
            if (existing != null) {
                return Optional.empty();
            }
            save();
            return Optional.of(user);
        }

        Optional<User> findByEmail(String email) {
            return Optional.ofNullable(users.get(email));
        }

        void updateProfile(String email, Profile profile) {
            users.computeIfPresent(email, (key, user) -> new User(user.email(), user.name(), user.passwordHash(), profile, user.createdAt(), user.friendEmails()));
            save();
        }

        List<User> friendsOf(User user) {
            return user.friendEmails().stream()
                    .map(users::get)
                    .filter(friend -> friend != null)
                    .toList();
        }

        synchronized FriendAddResult addFriendByCode(String email, String friendCode) {
            User user = users.get(email);
            Optional<User> target = findByFriendCode(friendCode);
            if (user == null || target.isEmpty()) {
                return FriendAddResult.NOT_FOUND;
            }

            User friend = target.get();
            if (user.email().equals(friend.email())) {
                return FriendAddResult.SELF;
            }
            if (user.friendEmails().contains(friend.email())) {
                return FriendAddResult.ALREADY_FRIENDS;
            }

            users.put(user.email(), user.withFriend(friend.email()));
            users.put(friend.email(), friend.withFriend(user.email()));
            save();
            return FriendAddResult.ADDED;
        }

        Optional<User> findByFriendCode(String friendCode) {
            String normalizedCode = normalizeFriendCode(friendCode);
            if (normalizedCode.isBlank()) {
                return Optional.empty();
            }
            return users.values().stream()
                    .filter(user -> friendCodeFor(user.email()).equals(normalizedCode))
                    .findFirst();
        }

        private synchronized void save() {
            try {
                Files.createDirectories(file.getParent());
                List<String> lines = new ArrayList<>();
                for (User user : users.values()) {
                    lines.add(encodeUser(user));
                }
                Files.write(file, lines, StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        }

        private String encodeUser(User user) {
            Profile profile = user.profile();
            return String.join("\t",
                    encode(user.email()),
                    encode(user.name()),
                    encode(user.passwordHash()),
                    encode(profile.age()),
                    encode(profile.city()),
                    encode(profile.goal()),
                    encode(profile.interests()),
                    encode(profile.about()),
                    encode(user.createdAt().toString()),
                    encode(String.join(",", user.friendEmails()))
            );
        }

        private User decodeUser(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 9 && parts.length != 10) {
                throw new IllegalStateException("Invalid user record in " + file);
            }

            Profile profile = new Profile(
                    decode(parts[3]),
                    decode(parts[4]),
                    decode(parts[5]),
                    decode(parts[6]),
                    decode(parts[7])
            );
            List<String> friendEmails = parts.length == 10
                    ? parseFriendEmails(decode(parts[9]))
                    : List.of();
            return new User(decode(parts[0]), decode(parts[1]), decode(parts[2]), profile, LocalDate.parse(decode(parts[8])), friendEmails);
        }

        private List<String> parseFriendEmails(String value) {
            if (value.isBlank()) {
                return List.of();
            }

            Set<String> uniqueEmails = new LinkedHashSet<>();
            for (String email : value.split(",")) {
                String normalizedEmail = normalizeEmail(email);
                if (!normalizedEmail.isBlank()) {
                    uniqueEmails.add(normalizedEmail);
                }
            }
            return List.copyOf(uniqueEmails);
        }

        private String encode(String value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private String decode(String value) {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    static class SessionStore {
        private final Map<String, String> sessions = new ConcurrentHashMap<>();

        String create(String email) {
            String sessionId = UUID.randomUUID().toString();
            sessions.put(sessionId, email);
            return sessionId;
        }

        Optional<String> emailFor(String sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        void delete(String sessionId) {
            sessions.remove(sessionId);
        }
    }

    static class Passwords {
        private static final SecureRandom RANDOM = new SecureRandom();
        private static final int ITERATIONS = 120_000;
        private static final int KEY_LENGTH = 256;

        static String hash(String password) {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(password, salt);
            return ITERATIONS + ":" + base64(salt) + ":" + base64(hash);
        }

        static boolean verify(String password, String storedHash) {
            String[] parts = storedHash.split(":");
            if (parts.length != 3) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password, salt, Integer.parseInt(parts[0]));
            return MessageDigest.isEqual(expected, actual);
        }

        private static byte[] pbkdf2(String password, byte[] salt) {
            return pbkdf2(password, salt, ITERATIONS);
        }

        private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
            try {
                PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            } catch (Exception error) {
                throw new IllegalStateException("Unable to hash password", error);
            }
        }

        private static String base64(byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
    }
}
