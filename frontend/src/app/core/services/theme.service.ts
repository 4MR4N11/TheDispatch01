import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  private readonly THEME_KEY = 'blog-theme';
  readonly isDarkMode = signal(false);

  constructor() {
    this.initializeTheme();
  }

  private initializeTheme(): void {
    // Check localStorage for saved theme preference
    const savedTheme = localStorage.getItem(this.THEME_KEY);

    if (savedTheme === 'dark') {
      this.enableDarkMode();
    } else if (savedTheme === 'light') {
      this.enableLightMode();
    } else {
      // Check system preference
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
      if (prefersDark) {
        this.enableDarkMode();
      }
    }
  }

  toggleTheme(): void {
    if (this.isDarkMode()) {
      this.enableLightMode();
    } else {
      this.enableDarkMode();
    }
  }

  private enableDarkMode(): void {
    document.documentElement.setAttribute('data-theme', 'dark');
    this.isDarkMode.set(true);
    localStorage.setItem(this.THEME_KEY, 'dark');
  }

  private enableLightMode(): void {
    document.documentElement.removeAttribute('data-theme');
    this.isDarkMode.set(false);
    localStorage.setItem(this.THEME_KEY, 'light');
  }
}
