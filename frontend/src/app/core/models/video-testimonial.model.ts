export interface VideoTestimonial {
  readonly id: string;
  readonly authorName: string;
  readonly embedUrl: string;
  readonly platform: 'youtube' | 'instagram';
}
