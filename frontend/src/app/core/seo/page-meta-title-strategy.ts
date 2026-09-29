import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, RouterStateSnapshot, TitleStrategy } from '@angular/router';

export const SITE_ORIGIN = 'https://sevillasinluz.es';

/** Sets the document title (from route `title`) plus description, canonical and og:url (from route `data`). */
@Injectable({ providedIn: 'root' })
export class PageMetaTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  private readonly document = inject(DOCUMENT);

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const title = this.buildTitle(snapshot);
    if (title !== undefined) this.title.setTitle(title);

    const description = this.deepestData(snapshot.root)['description'];
    if (typeof description === 'string') {
      this.meta.updateTag({ name: 'description', content: description });
    }

    const canonical = SITE_ORIGIN + this.pathOf(snapshot.url);
    this.setCanonical(canonical);
    this.meta.updateTag({ property: 'og:url', content: canonical });
  }

  /** Path without query string or fragment; the home page is always "/". */
  private pathOf(url: string): string {
    const path = url.split(/[?#]/)[0];
    return path.startsWith('/') ? path : `/${path}`;
  }

  private setCanonical(href: string): void {
    let link = this.document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
    if (!link) {
      link = this.document.createElement('link');
      link.setAttribute('rel', 'canonical');
      this.document.head.appendChild(link);
    }
    link.setAttribute('href', href);
  }

  private deepestData(route: ActivatedRouteSnapshot): Record<string, unknown> {
    let current = route;
    while (current.firstChild) current = current.firstChild;
    return current.data;
  }
}
