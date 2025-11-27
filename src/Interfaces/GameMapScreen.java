package Interfaces;

import Runner.MainScreen;
import Characters.Hero;
import Logic.Game;
import com.almasb.fxgl.dsl.FXGL;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GameMapScreen {

    private final StackPane root;
    private final Pane container;
    private final ImageView mapView;
    private final ImageView heroView;
    private final Scale containerScale;

    private double lastMouseX, lastMouseY;
    private boolean draggingMap = false;
    private boolean up, down, left, right;
    private final AnimationTimer mover;
    private double vx = 0, vy = 0;
    private final double SPEED = 180.0;

    private final double mapW;
    private final double mapH;

    private MediaPlayer mapMusic;

    // Obstáculos: villas
    private final List<Obstacle> obstacles = new ArrayList<>();
    private Obstacle currentInteractable = null;

    public enum Direction { NONE, N, NE, E, SE, S, SW, W, NW }
    private Direction currentDirection = Direction.NONE;

    // Colisión reducida: 22% del tamaño visual
    private static final double VILLAGE_COLLISION_SCALE = 0.22;

    public GameMapScreen(Game game) {
        Hero hero = game != null ? game.getHero() : null;

        Image mapImg = null;
        try {
            mapImg = new Image(getClass().getResourceAsStream("/Resources/textures/map.png"));
        } catch (Throwable ignored) {
            mapImg = null;
        }

        if (mapImg == null) {
            mapW = 800;
            mapH = 600;
        } else {
            mapW = mapImg.getWidth() > 0 ? mapImg.getWidth() : 800;
            mapH = mapImg.getHeight() > 0 ? mapImg.getHeight() : 600;
        }

        mapView = new ImageView(mapImg);
        mapView.setPreserveRatio(false);
        mapView.setSmooth(true);
        mapView.setFitWidth(mapW);
        mapView.setFitHeight(mapH);

        container = new Pane();
        container.setPrefSize(mapW, mapH);
        container.getChildren().add(mapView);

        heroView = createHeroView(hero);
        container.getChildren().add(heroView);

        containerScale = new Scale(1.0, 1.0, 0, 0);
        container.getTransforms().add(containerScale);

        root = new StackPane();
        root.setPrefSize(800, 600);
        root.getChildren().add(container);

        root.addEventFilter(MouseEvent.ANY, MouseEvent::consume);

        populateVillagesFromList();

        installControls();
        installEscHandler();

        mover = new AnimationTimer() {
            private long last = -1;
            @Override
            public void handle(long now) {
                if (last < 0) last = now;
                double dt = (now - last) / 1e9;
                last = now;
                updateVelocity();
                if (vx == 0 && vy == 0) return;
                double dx = vx * dt;
                double dy = vy * dt;
                moveHeroWithCollision(dx, dy);
            }
        };

        root.sceneProperty().addListener((obs, old, nw) -> {
            if (nw != null) {
                Platform.runLater(() -> {
                    positionHeroCenter();
                    root.requestFocus();
                    mover.start();
                });
            } else {
                mover.stop();
            }
        });
    }

    private ImageView createHeroView(Hero hero) {
        Image img = null;
        if (hero != null) {
            try { img = hero.getImage(); } catch (Throwable ignored) { img = null; }
        }
        if (img == null) {
            try { img = new Image(getClass().getResourceAsStream("/Resources/sprites/hero.png")); } catch (Throwable ignored) { img = null; }
        }
        ImageView iv = new ImageView(img);
        iv.setPreserveRatio(true);
        iv.setFitWidth(48);
        iv.setFitHeight(48);
        iv.setMouseTransparent(true);
        return iv;
    }

    private void positionHeroCenter() {
        double hw = heroView.getBoundsInLocal().getWidth();
        double hh = heroView.getBoundsInLocal().getHeight();
        heroView.setLayoutX((mapW - hw) / 2.0);
        heroView.setLayoutY((mapH - hh) / 2.0);
    }

    private void installControls() {
        root.addEventFilter(ScrollEvent.SCROLL, ev -> {
            if (ev.isControlDown()) {
                double delta = ev.getDeltaY() > 0 ? 1.1 : 0.9;
                double newScale = clamp(containerScale.getX() * delta, 0.4, 3.5);
                containerScale.setX(newScale);
                containerScale.setY(newScale);
                ev.consume();
            }
        });

        mapView.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                draggingMap = true;
                root.setCursor(Cursor.CLOSED_HAND);
                e.consume();
            }
        });

        mapView.setOnMouseDragged(e -> {
            if (draggingMap) {
                double dx = e.getSceneX() - lastMouseX;
                double dy = e.getSceneY() - lastMouseY;
                container.setTranslateX(container.getTranslateX() + dx);
                container.setTranslateY(container.getTranslateY() + dy);
                lastMouseX = e.getSceneX();
                lastMouseY = e.getSceneY();
                e.consume();
            }
        });

        mapView.setOnMouseReleased(e -> {
            draggingMap = false;
            root.setCursor(Cursor.DEFAULT);
            e.consume();
        });

        mapView.setOnMouseClicked(e -> {
            Point2D mapPoint = sceneToMap(e.getSceneX(), e.getSceneY());
            System.out.println("Click map coords: " + mapPoint);
            e.consume();
        });

        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            KeyCode k = ev.getCode();
            if (k == KeyCode.ENTER) {
                ev.consume();
                if (currentInteractable != null) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setTitle("Interacción");
                        a.setHeaderText(null);
                        a.setContentText("Has interactuado con una villa");
                        a.showAndWait();
                    });
                }
                return;
            }
            if (k == KeyCode.P) {
                ev.consume();
                System.out.println("Hero map top-left: " + getHeroMapTopLeft());
                System.out.println("Hero map center: " + getHeroMapCenter());
                System.out.println("Hero scene center: " + getHeroSceneCenter());
                System.out.println("Hero direction: " + getHeroDirection());
                return;
            }
            if (k == KeyCode.W || k == KeyCode.UP) up = true;
            if (k == KeyCode.S || k == KeyCode.DOWN) down = true;
            if (k == KeyCode.A || k == KeyCode.LEFT) left = true;
            if (k == KeyCode.D || k == KeyCode.RIGHT) right = true;
        });

        root.addEventFilter(KeyEvent.KEY_RELEASED, ev -> {
            KeyCode k = ev.getCode();
            if (k == KeyCode.W || k == KeyCode.UP) up = false;
            if (k == KeyCode.S || k == KeyCode.DOWN) down = false;
            if (k == KeyCode.A || k == KeyCode.LEFT) left = false;
            if (k == KeyCode.D || k == KeyCode.RIGHT) right = false;
        });

        root.setFocusTraversable(true);
    }

    private void installEscHandler() {
        root.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() == KeyCode.ESCAPE) {
                ev.consume();
                confirmReturnToMenu();
            }
        });
    }

    private void confirmReturnToMenu() {
        Platform.runLater(() -> {
            Alert dlg = new Alert(Alert.AlertType.CONFIRMATION);
            dlg.setTitle("Volver al menú");
            dlg.setHeaderText("¿Quieres volver al menú principal?");
            dlg.setContentText("Si vuelves al menú, la partida seguirá guardada en disco.");
            Optional<ButtonType> opt = dlg.showAndWait();
            if (opt.isPresent() && opt.get() == ButtonType.OK) {
                stopMapMusic();
                try { FXGL.getGameScene().removeUINode(root); } catch (Throwable ignored) {}
                MainScreen.restoreMenuAndMusic();
            }
        });
    }

    private void updateVelocity() {
        vx = 0;
        vy = 0;
        if (left) vx -= SPEED;
        if (right) vx += SPEED;
        if (up) vy -= SPEED;
        if (down) vy += SPEED;

        if (vx != 0 || vy != 0) {
            Direction newDir = directionFromVector(vx, vy);
            setDirectionIfChanged(newDir);
        } else {
            setDirectionIfChanged(Direction.NONE);
        }
    }

    private void moveHeroWithCollision(double dx, double dy) {
        currentInteractable = null;
        double hw = heroView.getBoundsInLocal().getWidth();
        double hh = heroView.getBoundsInLocal().getHeight();

        double curX = heroView.getLayoutX();
        double curY = heroView.getLayoutY();

        double proposedX = curX + dx;
        double proposedY = curY + dy;

        if (proposedX < 0) proposedX = 0;
        if (proposedY < 0) proposedY = 0;
        if (proposedX + hw > mapW) proposedX = mapW - hw;
        if (proposedY + hh > mapH) proposedY = mapH - hh;

        Rectangle2D heroRect = new Rectangle2D(proposedX, proposedY, hw, hh);

        boolean blocked = false;
        for (Obstacle ob : obstacles) {
            if (heroRect.intersects(ob.collisionRect)) {
                currentInteractable = ob;
                blocked = true;
                break;
            }
        }

        if (!blocked) {
            heroView.setLayoutX(proposedX);
            heroView.setLayoutY(proposedY);
        } else {
            setDirectionIfChanged(Direction.NONE);
        }
    }

    public void show() {
        Platform.runLater(() -> {
            MainScreen.hideMenu();
            startMapMusic();
            FXGL.getGameScene().addUINode(root);
            root.requestFocus();
            drawDebugObstacles();
        });
    }

    private void startMapMusic() {
        try {
            stopMapMusic();
            URL res = getClass().getResource("/Resources/music/gameMapScreen.mp3");
            if (res == null) return;
            Media media = new Media(res.toExternalForm());
            mapMusic = new MediaPlayer(media);
            mapMusic.setCycleCount(MediaPlayer.INDEFINITE);
            double vol = MainScreen.getVolumeSetting();
            mapMusic.setVolume(vol);
            mapMusic.play();
        } catch (Throwable ignored) { }
    }

    private void stopMapMusic() {
        try {
            if (mapMusic != null) {
                mapMusic.stop();
                mapMusic.dispose();
                mapMusic = null;
            }
        } catch (Throwable ignored) { }
    }

    private static double clamp(double v, double lo, double hi) {
        double out = v;
        if (out < lo) out = lo;
        else if (out > hi) out = hi;
        return out;
    }

    private Direction directionFromVector(double vx, double vy) {
        if (vx == 0 && vy == 0) return Direction.NONE;
        double angle = Math.toDegrees(Math.atan2(-vy, vx));
        if (angle < 0) angle += 360.0;

        if (angle >= 337.5 || angle < 22.5) return Direction.E;
        if (angle >= 22.5 && angle < 67.5) return Direction.NE;
        if (angle >= 67.5 && angle < 112.5) return Direction.N;
        if (angle >= 112.5 && angle < 157.5) return Direction.NW;
        if (angle >= 157.5 && angle < 202.5) return Direction.W;
        if (angle >= 202.5 && angle < 247.5) return Direction.SW;
        if (angle >= 247.5 && angle < 292.5) return Direction.S;
        if (angle >= 292.5 && angle < 337.5) return Direction.SE;
        return Direction.NONE;
    }

    private void setDirectionIfChanged(Direction newDir) {
        if (newDir == null) newDir = Direction.NONE;
        currentDirection = newDir;
    }

    public Direction getHeroDirection() {
        return currentDirection;
    }

    public Point2D getHeroMapTopLeft() {
        return new Point2D(heroView.getLayoutX(), heroView.getLayoutY());
    }

    public Point2D getHeroMapCenter() {
        double cx = heroView.getLayoutX() + heroView.getBoundsInLocal().getWidth() / 2.0;
        double cy = heroView.getLayoutY() + heroView.getBoundsInLocal().getHeight() / 2.0;
        return new Point2D(cx, cy);
    }

    public Point2D getHeroSceneCenter() {
        double localCx = heroView.getBoundsInLocal().getWidth() / 2.0;
        double localCy = heroView.getBoundsInLocal().getHeight() / 2.0;
        return heroView.localToScene(localCx, localCy);
    }

    public Point2D sceneToMap(double sceneX, double sceneY) {
        return container.sceneToLocal(sceneX, sceneY);
    }

    public Point2D mapToScene(double mapX, double mapY) {
        return container.localToScene(mapX, mapY);
    }

    private static class Obstacle {
        final Rectangle2D visualRect;
        final Rectangle2D collisionRect;
        Obstacle(Rectangle2D visual, Rectangle2D collision) {
            visualRect = visual;
            collisionRect = collision;
        }
    }

    private void addVillageAtCenter(Point2D center, double visualW, double visualH) {
        double vx = center.getX() - visualW / 2.0;
        double vy = center.getY() - visualH / 2.0;
        Rectangle2D visual = new Rectangle2D(vx, vy, visualW, visualH);

        double cw = visualW * VILLAGE_COLLISION_SCALE;
        double ch = visualH * VILLAGE_COLLISION_SCALE;
        double cx = center.getX() - cw / 2.0;
        double cy = center.getY() - ch / 2.0;
        Rectangle2D collision = new Rectangle2D(cx, cy, cw, ch);

        obstacles.add(new Obstacle(visual, collision));
    }

    private void populateVillagesFromList() {
        obstacles.clear();

        Point2D[] villageCenters = new Point2D[] {
            new Point2D(407.0, 358.5165319999998),
            new Point2D(435.8005579999999, 358.5165319999998),
            new Point2D(458.8777639999999, 358.5165319999998),
            new Point2D(192.526778, 152.8527799999999),
            new Point2D(169.48557200000005, 152.8527799999999),
            new Point2D(218.316764, 152.8527799999999),
            new Point2D(51.34460599999994, 121.2158539999999),
            new Point2D(25.328647999999937, 121.2158539999999),
            new Point2D(77.27695399999993, 121.2158539999999),
            new Point2D(405.6877819999999, 121.1205079999999),
            new Point2D(431.47821799999986, 121.1205079999999),
            new Point2D(466.0058359999998, 121.1205079999999),
            new Point2D(399.6368480000001, 78.16393999999985),
            new Point2D(693.5160080000004, 262.65198799999985),
            new Point2D(722.2856060000005, 262.65198799999985),
            new Point2D(745.1885540000004, 262.65198799999985)
        };

        // Visual reducido para que la colisión sea más ajustada
        double visualW = 64;
        double visualH = 48;

        for (Point2D c : villageCenters) {
            addVillageAtCenter(c, visualW, visualH);
        }
    }

    private void drawDebugObstacles() {
        container.getChildren().removeIf(n -> "debug".equals(n.getProperties().get("tag")));
        for (Obstacle ob : obstacles) {
            if (ob.visualRect != null) {
                Rectangle rv = new Rectangle(
                        ob.visualRect.getMinX(), ob.visualRect.getMinY(),
                        ob.visualRect.getWidth(), ob.visualRect.getHeight());
                rv.setFill(javafx.scene.paint.Color.rgb(0, 0, 255, 0.12));
                rv.setStroke(javafx.scene.paint.Color.rgb(0, 0, 120, 0.5));
                rv.getProperties().put("tag", "debug");
                rv.setMouseTransparent(true);
                container.getChildren().add(rv);
            }

            Rectangle rc = new Rectangle(
                    ob.collisionRect.getMinX(), ob.collisionRect.getMinY(),
                    ob.collisionRect.getWidth(), ob.collisionRect.getHeight());
            rc.setFill(javafx.scene.paint.Color.rgb(255, 0, 0, 0.22));
            rc.setStroke(javafx.scene.paint.Color.rgb(120, 0, 0, 0.6));
            rc.getProperties().put("tag", "debug");
            rc.setMouseTransparent(true);
            container.getChildren().add(rc);
        }
    }
}
