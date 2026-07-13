import {
  afterNextRender,
  Component,
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

import type { Neighborhood } from '../../../core/models';
import type { EnelOutage } from '../../../core/services/api-outage.service';
import { parseMadridDate } from '../../../core/utils/madrid-date';

const MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
const COLORS = [
  '#e6194b', '#3cb44b', '#4363d8', '#f58231',
  '#911eb4', '#42d4f4', '#f032e6', '#469990',
  '#9a6324', '#800000', '#000075', '#808080',
];

@Component({
  selector: 'app-chart',
  imports: [],
  templateUrl: './chart.component.html',
})
export class ChartComponent {
  readonly neighborhoods = input.required<readonly Neighborhood[]>();
  readonly outages = input.required<readonly EnelOutage[]>();

  private readonly canvasRef = viewChild<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart?: Chart;

  protected readonly selectedIds = signal<Set<string>>(new Set());

  constructor() {
    afterNextRender(() => {
      const first = this.neighborhoods()[0];
      if (first) this.selectedIds.set(new Set([first.id]));
      this.initChart();
    });

    effect(() => {
      // Re-evaluate whenever the inputs change so the chart stays in sync.
      this.outages();
      this.selectedIds();
      this.updateChart();
    });
  }

  protected toggleNeighborhood(id: string): void {
    this.selectedIds.update(ids => {
      const next = new Set(ids);
      if (next.has(id) && next.size > 1) next.delete(id);
      else next.add(id);
      return next;
    });
    this.updateChart();
  }

  private initChart(): void {
    const canvas = this.canvasRef()?.nativeElement;
    if (!canvas) return;
    this.chart = new Chart(canvas, { type: 'line', data: { labels: MONTHS, datasets: [] }, options: this.options() });
    this.updateChart();
  }

  private updateChart(): void {
    if (!this.chart) return;
    const neighborhoods = this.neighborhoods();
    const outages = this.outages();
    const selected = this.selectedIds();

    this.chart.data.datasets = neighborhoods
      .filter(n => selected.has(n.id))
      .map(n => {
        const globalIndex = neighborhoods.indexOf(n);
        const monthlyCounts = MONTHS.map((_, monthIdx) =>
          outages.filter(o => o.neighborhoodName === n.name && parseMadridDate(o.interruptionDate).getMonth() === monthIdx).length
        );
        const color = COLORS[globalIndex % COLORS.length];
        return {
          label: n.name, data: monthlyCounts, borderColor: color,
          backgroundColor: `${color}12`, borderWidth: 2.5, pointRadius: 4,
          pointHoverRadius: 7, tension: 0.35, fill: true,
        };
      });
    this.chart.update('none');
  }

  private options(): ChartOptions<'line'> {
    return {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: { legend: { display: false } },
      scales: {
        x: { grid: { color: 'rgba(209,213,219,0.4)' }, ticks: { color: '#9ca3af', font: { size: 11 } } },
        y: { beginAtZero: true, ticks: { stepSize: 1, color: '#9ca3af', font: { size: 11 } },
             grid: { color: 'rgba(209,213,219,0.4)' } },
      },
    };
  }
}
