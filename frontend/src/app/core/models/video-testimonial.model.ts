export interface VideoTestimonial {
  readonly id: number;
  readonly authorName: string;
  readonly embedUrl: string;
  readonly platform: 'youtube' | 'instagram';
}
