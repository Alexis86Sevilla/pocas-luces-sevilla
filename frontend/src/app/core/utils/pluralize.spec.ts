import { pluralize } from './pluralize';

describe('pluralize', () => {
  it('uses the singular form for exactly one', () => {
    expect(pluralize(1, 'corte', 'cortes')).toBe('corte');
  });

  it('uses the plural form for zero', () => {
    expect(pluralize(0, 'corte', 'cortes')).toBe('cortes');
  });

  it('uses the plural form for more than one', () => {
    expect(pluralize(5, 'corte', 'cortes')).toBe('cortes');
  });

  it('defaults the plural form to singular + "s" when omitted', () => {
    expect(pluralize(3, 'afectado')).toBe('afectados');
    expect(pluralize(1, 'afectado')).toBe('afectado');
  });
});
