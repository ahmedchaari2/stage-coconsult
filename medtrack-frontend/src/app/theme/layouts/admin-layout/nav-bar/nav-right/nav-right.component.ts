import { Component, output, inject, input } from '@angular/core';
import { RouterModule, Router } from '@angular/router';

import { SharedModule } from 'src/app/theme/shared/shared.module';
import { AuthService } from 'src/app/core/services/auth.service';
import { LanguageService, SupportedLang } from 'src/app/core/services/language.service';
import { NotificationService } from 'src/app/core/services/notification.service';
import { AppNotification } from 'src/app/core/models/notification.model';
import { formatAppointmentNotification, formatRelativeTime } from 'src/app/core/utils/notification-message';
import { TranslocoModule, TranslocoService } from '@jsverse/transloco';

import { IconService } from '@ant-design/icons-angular';
import {
  BellOutline,
  SettingOutline,
  CalendarOutline,
  LogoutOutline,
  EditOutline,
  UserOutline,
  ProfileOutline,
  WalletOutline,
  QuestionCircleOutline,
  LockOutline,
  CommentOutline,
  UnorderedListOutline,
  ArrowRightOutline
} from '@ant-design/icons-angular/icons';

@Component({
  selector: 'app-nav-right',
  imports: [SharedModule, RouterModule, TranslocoModule],
  templateUrl: './nav-right.component.html',
  styleUrls: ['./nav-right.component.scss']
})
export class NavRightComponent {
  private iconService = inject(IconService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private languageService = inject(LanguageService);
  private notificationService = inject(NotificationService);
  private translocoService = inject(TranslocoService);

  styleSelectorToggle = input<boolean>();
  readonly Customize = output();
  windowWidth: number;
  screenFull: boolean = true;
  direction: string = 'ltr';

  constructor() {
    this.windowWidth = window.innerWidth;
    this.iconService.addIcon(
      ...[
        SettingOutline,
        CalendarOutline,
        LogoutOutline,
        EditOutline,
        UserOutline,
        ProfileOutline,
        QuestionCircleOutline,
        LockOutline,
        CommentOutline,
        UnorderedListOutline,
        ArrowRightOutline,
        BellOutline,
        WalletOutline
      ]
    );
  }

  readonly notifications = this.notificationService.notifications;
  readonly unreadCount = this.notificationService.unreadCount;
  readonly hasMoreNotifications = this.notificationService.hasMore;
  readonly loadingMoreNotifications = this.notificationService.loadingMore;

  onNotificationDropdownOpenChange(open: boolean): void {
    if (open) {
      this.notificationService.markAllAsRead();
    }
  }

  notificationLabel(n: AppNotification): string {
    return formatAppointmentNotification(n, this.translocoService);
  }

  notificationRelativeTime(n: AppNotification): string {
    return formatRelativeTime(n.createdAt, this.translocoService);
  }

  onNotificationClick(n: AppNotification): void {
    this.notificationService.markAsRead(n.id);
    this.router.navigate(['/appointments'], { queryParams: { appointmentId: n.appointmentId } });
  }

  onLoadMoreNotifications(): void {
    this.notificationService.loadMore();
  }

  get currentUser() {
    return this.authService.currentUser();
  }

  get userFullName(): string {
    const user = this.currentUser;
    return user ? `${user.prenom} ${user.nom}` : 'Utilisateur';
  }

  get userRole(): string {
    const user = this.currentUser;
    return user?.role ?? '';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  get currentLang(): SupportedLang {
    return this.languageService.currentLang();
  }

  setLanguage(lang: SupportedLang): void {
    this.languageService.setLanguage(lang);
  }

  profile: { icon: string; title: string; route: string | null }[] = [
    {
      icon: 'edit',
      title: 'nav.profile.editProfile',
      route: '/profile/edit'
    },
    {
      icon: 'user',
      title: 'nav.profile.viewProfile',
      route: '/profile/view'
    },
    {
      icon: 'logout',
      title: 'nav.profile.logout',
      route: null // géré par la méthode logout()
    }
  ];

  setting = [
    {
      icon: 'question-circle',
      title: 'Support'
    },
    {
      icon: 'user',
      title: 'Account Settings'
    },
    {
      icon: 'lock',
      title: 'Privacy Center'
    },
    {
      icon: 'comment',
      title: 'Feedback'
    },
    {
      icon: 'unordered-list',
      title: 'History'
    }
  ];
}
