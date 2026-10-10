package td.teladoumbaobabtd;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import td.teladoumbaobabtd.repository.NotificationRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Activité affichant la liste des notifications reçues pour les commentaires et réactions.
 * Permet d'ouvrir le post concerné et de marquer les notifications comme lues.
 */
public class NotificationActivity extends AppCompatActivity {

    private RecyclerView rvNotifications;
    private View layoutEmptyNotifications;
    private SwipeRefreshLayout swipeRefreshNotifications;

    private NotificationRepository notificationRepository;
    private SessionManager sessionManager;

    private NotificationAdapter adapter;
    private final List<AppNotification> notificationList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        Toolbar toolbar = findViewById(R.id.toolbarNotifications);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        sessionManager = new SessionManager(this);
        notificationRepository = new NotificationRepository(this);

        rvNotifications = findViewById(R.id.rvNotifications);
        layoutEmptyNotifications = findViewById(R.id.layoutEmptyNotifications);
        swipeRefreshNotifications = findViewById(R.id.swipeRefreshNotifications);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));

        adapter = new NotificationAdapter(notificationList, notification -> {
            // Clic sur une notification : Redirection explicite selon le targetType et le type
            notificationRepository.markAllAsRead(sessionManager.getUserId());
            String targetType = notification.getTargetType();
            String type = notification.getType();

            if ("POST".equalsIgnoreCase(targetType) ||
                    "POST_LIKE".equals(type) ||
                    "POST_REACTION".equals(type) ||
                    "POST_COMMENT".equals(type)) {
                Intent intent = new Intent(NotificationActivity.this, CommenterActivity.class);
                intent.putExtra("post_id", notification.getTargetId());
                startActivity(intent);
            } else if ("USER".equalsIgnoreCase(targetType) || "FRIEND_REQUEST".equals(type) || "FRIEND_ACCEPT".equals(type)) {
                Intent intent = new Intent(NotificationActivity.this, AmiActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(NotificationActivity.this, AcceuilActivity.class);
                startActivity(intent);
            }
        });

        rvNotifications.setAdapter(adapter);

        if (swipeRefreshNotifications != null) {
            swipeRefreshNotifications.setOnRefreshListener(() -> {
                loadNotifications();
                swipeRefreshNotifications.setRefreshing(false);
            });
        }

        loadNotifications();
    }

    private void loadNotifications() {
        if (notificationRepository == null) return;

        List<AppNotification> newNotifications = notificationRepository.getNotificationsForUser(sessionManager.getUserId());
        notificationList.clear();
        notificationList.addAll(newNotifications);
        adapter.notifyDataSetChanged();

        boolean isEmpty = notificationList.isEmpty();
        if (layoutEmptyNotifications != null) {
            layoutEmptyNotifications.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (rvNotifications != null) {
            rvNotifications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 2001, 0, "Tout marquer comme lu").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 2001) {
            notificationRepository.markAllAsRead(sessionManager.getUserId());
            loadNotifications();
            Toasty.success(this, "Toutes les notifications ont été marquées comme lues", Toasty.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }
}