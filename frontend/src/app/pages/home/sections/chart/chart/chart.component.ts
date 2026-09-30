import {
  afterNextRender,
  Component,
  computed,
  effect,
  ElementRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import {
  Chart,
  CategoryScale,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
  type ChartOptions,
} from 'chart.js';

Chart.register(LineController, LineElement, PointElement, LinearScale, CategoryScale, Tooltip, Legend);

import type { District } from '../../../../../core/models';
import type { EnelOutage } from '../../../../../core/services/api-outage.service';
import { countByDistrictAndMonth } from '../../../../../core/utils/monthly-counts';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
const FULL_MONTHS = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];
const NUMBER_FORMAT = new Intl.NumberFormat('es-ES');
const outagesLabel = (n: number) => `${NUMBER_FORMAT.format(n)} ${n === 1 ? 'corte' : 'cortes'}`;
// '#1f2937' replaces the original '#808080' (gray), which had low contrast on the light card background.
const COLORS = [
  '#e6194b', '#3cb44b', '#4363d8', '#f58231',
  '#911eb4', '#42d4f4', '#f032e6', '#469990',
  '#9a6324', '#800000', '#000075', '#1f2937',
];

export interface MonthlyTableRow {
  readonly month: string;
  readonly counts: readonly number[];
}

@Component({
  selector: 'app-chart',
  imports: [],
  templateUrl: './chart.component.html',
})
export class ChartComponent {
  readonly districts = input.required<readonly District[]>();
  readonly outages = input.required<readonly EnelOutage[]>();

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart?: Chart;

  protected readonly selectedIds = signal<Set<string>>(new Set());

  /** Counts per district and month, computed once per data change (not per click). */
  private readonly counts = computed(() => countByDistrictAndMonth(this.outages()));

  // Accessible alternative to the canvas chart: the same monthly counts as a table.
  protected readonly selectedDistricts = computed(() =>
    this.districts().filter(d => this.selectedIds().has(d.id))
  );

  protected readonly tableRows = computed((): readonly MonthlyTableRow[] => {
    const districts = this.selectedDistricts();
    return MONTHS.map((month, monthIdx) => ({
      month,
      counts: districts.map(d => this.monthlyCount(d, monthIdx)),
    }));
  });

  constructor() {
    afterNextRender(() => {
      const first = this.districts()[0];
      if (first) this.selectedIds.set(new Set([first.id]));
      this.initChart();
    });

    // Single re-render path: this effect reacts to both outages() and selectedIds(),
    // so toggleDistrict() must not call updateChart() itself (that caused a double render).
    effect(() => {
      this.outages();
      this.selectedIds();
      this.updateChart();
    });
  }

  protected toggleDistrict(id: string): void {
    this.selectedIds.update(ids => {
      const next = new Set(ids);
      if (next.has(id) && next.size > 1) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  protected isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  private initChart(): void {
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) return;
    this.chart = new Chart(canvas, { type: 'line', data: { labels: MONTHS, datasets: [] }, options: this.options() });
    this.updateChart();
  }

  private monthlyCount(district: District, monthIdx: number): number {
    return this.counts().get(district.name)?.[monthIdx] ?? 0;
  }

  private updateChart(): void {
    if (!this.chart) return;
    const districts = this.districts();
    const selected = this.selectedIds();

    this.chart.data.datasets = districts
      .filter(d => selected.has(d.id))
      .map(d => {
        const globalIndex = districts.indexOf(d);
        const monthlyCounts = MONTHS.map((_, monthIdx) => this.monthlyCount(d, monthIdx));
        const color = COLORS[globalIndex % COLORS.length];
        return {
          label: d.name, data: monthlyCounts, borderColor: color,
          backgroundColor: `${color}12`, borderWidth: 2.5, pointRadius: 5,
          pointHoverRadius: 8, pointBackgroundColor: '#ffffff',
          pointBorderColor: color, pointBorderWidth: 2.5,
          tension: 0.35, cubicInterpolationMode: 'monotone', fill: true,
        };
      });
    this.chart.update();
  }

  private options(): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#ffffff',
          borderColor: '#e5e7eb',
          borderWidth: 1,
          titleColor: '#111827',
          bodyColor: '#374151',
          footerColor: '#6b7280',
          titleFont: { size: 14, weight: 'bold' },
          bodyFont: { size: 13 },
          footerFont: { size: 12, weight: 'normal' },
          padding: 12,
          cornerRadius: 12,
          caretSize: 6,
          titleMarginBottom: 8,
          bodySpacing: 6,
          footerMarginTop: 8,
          boxPadding: 6,
          usePointStyle: true,
          displayColors: true,
          itemSort: (a, b) => (b.parsed.y ?? 0) - (a.parsed.y ?? 0),
          callbacks: {
            title: (items) => FULL_MONTHS[items[0]?.dataIndex ?? 0],
            label: (context) => `${context.dataset.label}  ·  ${outagesLabel(context.parsed.y ?? 0)}`,
            labelPointStyle: () => ({ pointStyle: 'circle', rotation: 0 }),
            labelColor: (context) => {
              const color = String(context.dataset.borderColor);
              return { borderColor: color, backgroundColor: color, borderWidth: 0, borderRadius: 4 };
            },
            footer: (items) => items.length > 1
              ? `Total: ${outagesLabel(items.reduce((sum, item) => sum + (item.parsed.y ?? 0), 0))}`
              : '',
          },
        },
      },
      scales: {
        x: { grid: { color: 'rgba(209,213,219,0.4)' }, ticks: { color: '#9ca3af', font: { size: 11 } } },
        y: { beginAtZero: true, ticks: { stepSize: 1, color: '#9ca3af', font: { size: 11 } },
             grid: { color: 'rgba(209,213,219,0.4)' } },
      },
    };
  }
}
