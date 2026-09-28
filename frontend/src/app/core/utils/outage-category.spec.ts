import { outageCategory } from './outage-category';

describe('outageCategory', () => {
  it('uses Endesa cause for scheduled works', () => {
    expect(outageCategory({ cause: 'Trabajos programados', serviceType: 'GB' })).toBe('Programado');
  });

  it('uses Endesa cause for breakdowns', () => {
    expect(outageCategory({ cause: 'Avería', serviceType: 'LV' })).toBe('Avería');
  });

  it('falls back to the service type when cause is missing', () => {
    expect(outageCategory({ cause: null, serviceType: 'LV' })).toBe('Programado');
    expect(outageCategory({ serviceType: 'GB' })).toBe('Avería');
  });
});
