import {
  afterNextRender,
  Component,
  computed,
  ElementRef,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Chart, type ChartOptions } from 'chart.js/auto';

import type { Neighborhood, OutageEvent } from '../../core/models';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];

const COLORS = ['#d97706', '#dc2626', '#7c3aed', '#0891b2', '#059669', '#ea580c', '#e11d48', '#2563eb', '#c026d3', '#65a30d', '#0d9488', '#f59e0b'];

@Component({
  selector: 'app-outage-chart',
  imports: [],
  templateUrl: './outage-chart.component.html',
})
export class OutageChartComponent {
  readonly neighborhoods = input.required<readonly Neighborhood[]>();
  readonly outages = input.required<readonly OutageEvent[]>();

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart?: Chart;

  protected readonly selectedIds = signal<Set<string>>(new Set());
  protected readonly selectedYear = signal<number>(new Date().getFullYear());

  protected readonly availableYears = computed(() => {
    const years = new Set(this.outages().map((o) => o.date.getFullYear()));
    return Array.from(years).sort((a, b) => b - a);
  });

  protected readonly filteredOutages = computed(() => {
    const year = this.selectedYear();
    return this.outages().filter((o) => o.date.getFullYear() === year);
  });

  constructor() {
    afterNextRender(() => {
      // Default: select only the first neighborhood
      const first = this.neighborhoods()[0];
      if (first) {
        this.selectedIds.set(new Set([first.id]));
      }
      this.initChart();
    });
  }

  protected toggleNeighborhood(id: string): void {
    this.selectedIds.update((ids) => {
      const next = new Set(ids);
      if (next.has(id) && next.size > 1) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
    this.updateChart();
  }

  protected onYearChange(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.selectedYear.set(value);
    this.updateChart();
  }

  private initChart(): void {
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) return;

    this.chart = new Chart(canvas, {
      type: 'line',
      data: { labels: MONTHS, datasets: [] },
      options: this.chartOptions(),
    });

    this.updateChart();
  }

  private updateChart(): void {
    if (!this.chart) return;

    const neighborhoods = this.neighborhoods();
    const outages = this.filteredOutages();
    const selected = this.selectedIds();

    this.chart.data.datasets = neighborhoods
      .filter((n) => selected.has(n.id))
      .map((n, i) => {
        const monthlyCounts = MONTHS.map((_, monthIdx) => {
          return outages.filter(
            (o) => o.neighborhoodId === n.id && o.date.getMonth() === monthIdx,
          ).length;
        });

        const color = COLORS[i % COLORS.length];
        return {
          label: n.name,
          data: monthlyCounts,
          borderColor: color,
          backgroundColor: `${color}12`,
          borderWidth: 2.5,
          pointRadius: 4,
          pointHoverRadius: 7,
          pointBackgroundColor: color,
          pointBorderColor: '#ffffff',
          pointBorderWidth: 2,
          tension: 0.35,
          fill: true,
        };
      });

    this.chart.update('none');
  }

  private chartOptions(): ChartOptions<'line'> {
    return {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#111827',
          titleColor: '#f9fafb',
          bodyColor: '#d1d5db',
          padding: 14,
          cornerRadius: 10,
          titleFont: { weight: 'bold', size: 13 },
          bodyFont: { size: 12 },
          displayColors: true,
          boxPadding: 4,
        },
      },
      scales: {
        x: {
          grid: { color: 'rgba(209, 213, 219, 0.4)', drawTicks: false },
          border: { display: false },
          ticks: { color: '#9ca3af', font: { size: 11 }, padding: 8 },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          ticks: {
            color: '#9ca3af',
            font: { size: 11 },
            stepSize: 1,
            padding: 8,
          },
          grid: { color: 'rgba(209, 213, 219, 0.4)', drawTicks: false },
        },
      },
    };
  }
}
