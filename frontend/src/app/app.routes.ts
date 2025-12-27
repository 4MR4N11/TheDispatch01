// app/app.routes.ts
import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { HomeComponent } from './features/home/home';
import { ProfileComponent } from './features/users/profile.component';
import { PostDetailComponent } from './features/posts/posts.component';
import { ReportsComponent } from './features/reports/reports.component';
import { AdminComponent } from './features/admin/admin.component';
import { AdminReportsComponent } from './features/admin/admin-reports/admin-reports.component';
import { AdminUsersComponent } from './features/admin/admin-users/admin-users.component';
import { AdminPostsComponent } from './features/admin/admin-posts/admin-posts.component';
import { MyBlogComponent } from './features/my-blog/my-blog.component';
import { EditProfileComponent } from './features/edit-profile/edit-profile.component';
import { NotificationsComponent } from './features/notifications/notifications.component';
import { AuthGuard } from './core/guard/auth-guard';
import { AdminGuard } from './core/auth/admin.guard';
import { NotFoundComponent } from './features/not-found/not-found.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'home', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'my-blog', component: MyBlogComponent, canActivate: [AuthGuard] },
  { path: 'edit-profile', component: EditProfileComponent, canActivate: [AuthGuard] },
  { path: 'profile/:username', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'post/:id', component: PostDetailComponent, canActivate: [AuthGuard] },
  { path: 'notifications', component: NotificationsComponent, canActivate: [AuthGuard] },
  { path: 'my-reports', component: ReportsComponent, canActivate: [AuthGuard] },
  {
    path: 'admin',
    component: AdminComponent,
    canActivate: [AuthGuard, AdminGuard],
    children: [
      { path: '', redirectTo: 'reports', pathMatch: 'full' },
      { path: 'reports', component: AdminReportsComponent },
      { path: 'users', component: AdminUsersComponent },
      { path: 'posts', component: AdminPostsComponent }
    ]
  },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', component: NotFoundComponent }
];