import { Component } from '@angular/core';

import { formatMadridDate } from '../../core/utils/madrid-date';
import { OpenDataDownloadComponent } from '../open-data-download/open-data-download.component';

/**
 * Public methodology section: what the site's figures actually mean, so readers (and
 * anyone citing this data) can judge exactly how much to trust each number.
 */
@Component({
  selector: 'app-methodology-section',
  imports: [OpenDataDownloadComponent],
  templateUrl: './methodology-section.component.html',
})
export class MethodologySectionComponent {
  protected readonly currentYear = Number(formatMadridDate(new Date(), 'yyyy'));

  /**
   * Columns of the public CSV (`/api/open-data/outages.csv`), in file order. Keep in sync with
   * OpenDataOutageRow in the backend and the README.
   */
  protected readonly csvColumns: readonly { readonly name: string; readonly description: string }[] = [
    { name: 'interruption_start', description: 'Inicio del corte, tal como lo publica la distribuidora. Hora local de Madrid.' },
    { name: 'estimated_restoration', description: 'Estimación de la distribuidora de cuándo volverá el suministro. Es una previsión, no el fin real. Hora local de Madrid; puede estar vacía.' },
    { name: 'observed_end', description: 'Última consulta en la que la distribuidora aún publicaba el corte; el fin real pudo ser hasta ~5 min después. Hora local de Madrid; vacía mientras el corte sigue activo.' },
    { name: 'observed_duration_min', description: 'Minutos enteros entre interruption_start y observed_end. Es un mínimo. Vacío si el corte sigue activo o si no hay un fin observado posterior al inicio.' },
    { name: 'affected_supply_points', description: 'Puntos de suministro afectados (viviendas o locales), no personas.' },
    { name: 'category', description: '«Avería» o «Programado», deducida de cause; si cause está vacía, se usa service_type (LV = programado).' },
    { name: 'cause', description: 'Causa publicada por la distribuidora («Avería» o «Trabajos programados»). Puede estar vacía en los cortes anteriores al 28/09/2026.' },
    { name: 'service_type', description: 'Tipo de servicio publicado por la distribuidora, tal cual.' },
    { name: 'district', description: 'Distrito, calculado con las coordenadas y los límites oficiales de los distritos de Sevilla.' },
    { name: 'neighborhood_approx', description: 'Barrio aproximado: el de referencia más cercano a las coordenadas; puede fallar cerca de los límites.' },
    { name: 'latitude', description: 'Latitud publicada por la distribuidora (grados decimales, WGS84).' },
    { name: 'longitude', description: 'Longitud publicada por la distribuidora (grados decimales, WGS84).' },
    { name: 'first_seen', description: 'Primera consulta nuestra en la que apareció el corte. Hora local de Madrid.' },
    { name: 'last_seen', description: 'Última consulta nuestra en la que se vio publicado. Hora local de Madrid.' },
    { name: 'active', description: '«true» si el corte seguía publicado en la última consulta; «false» si ya no.' },
  ];
}
