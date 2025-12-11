// app/app.routes.ts
import { Routes } from '@angular/router';
import { LoginComponent } from './features/auth/login/login.component';
import { RegisterComponent } from './features/auth/register/register.component';
import { HomeComponent } from './features/home/home';
import { ProfileComponent } from './features/users/profile.component';
import { PostDetailComponent } from './features/posts/posts.component';
import { ReportsComponent } from './features/reports/reports.component';
import { AdminComponent } from './features/admin/admin.component';
import { MyBlogComponent } from './features/my-blog/my-blog.component';
import { CreatePostComponent } from './features/create-post/create-post.component';
import { EditPostComponent } from './features/edit-post/edit-post.component';
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
  { path: 'create-post', component: CreatePostComponent, canActivate: [AuthGuard] },
  { path: 'edit-post/:id', component: EditPostComponent, canActivate: [AuthGuard] },
  { path: 'edit-profile', component: EditProfileComponent, canActivate: [AuthGuard] },
  { path: 'profile/:username', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [AuthGuard] },
  { path: 'post/:id', component: PostDetailComponent, canActivate: [AuthGuard] },
  { path: 'notifications', component: NotificationsComponent, canActivate: [AuthGuard] },
  { path: 'my-reports', component: ReportsComponent, canActivate: [AuthGuard] },
  { path: 'admin', component: AdminComponent, canActivate: [AuthGuard, AdminGuard] },
  { path: '', redirectTo: '/home', pathMatch: 'full' },
  { path: '**', component: NotFoundComponent }
];