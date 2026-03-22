import { CardLayoutService } from './card-layout.service';

describe('CardLayoutService', () => {
  let getItemSpy: jasmine.Spy;
  let setItemSpy: jasmine.Spy;

  beforeEach(() => {
    getItemSpy = spyOn(localStorage, 'getItem');
    setItemSpy = spyOn(localStorage, 'setItem');
  });

  it('reads layout from localStorage on construction', () => {
    getItemSpy.and.returnValue('2');
    const service = new CardLayoutService();
    expect(service.layout).toBe(2);
  });

  it('defaults to 1 when localStorage is empty', () => {
    getItemSpy.and.returnValue(null);
    const service = new CardLayoutService();
    expect(service.layout).toBe(1);
  });

  it('set writes to localStorage and updates layout', () => {
    getItemSpy.and.returnValue(null);
    const service = new CardLayoutService();

    service.set(3);

    expect(setItemSpy).toHaveBeenCalledWith('ph_card_layout', '3');
    expect(service.layout).toBe(3);
  });

  it('layout$ emits new values on set', () => {
    getItemSpy.and.returnValue(null);
    const service = new CardLayoutService();
    const emitted: number[] = [];
    service.layout$.subscribe(v => emitted.push(v));

    service.set(2);
    service.set(3);

    expect(emitted).toContain(2);
    expect(emitted).toContain(3);
  });
});
