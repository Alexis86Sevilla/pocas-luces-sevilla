export interface OutageEvent {
  readonly id: string;
  readonly date: Date;
  readonly durationMinutes: number;
  readonly neighborhoodId: string;
}
