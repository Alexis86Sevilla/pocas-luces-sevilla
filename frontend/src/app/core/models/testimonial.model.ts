export interface Testimonial {
  readonly id: string;
  readonly authorName: string;
  readonly quote: string;
  readonly source: string;
  readonly sourceUrl: string | null;
}
