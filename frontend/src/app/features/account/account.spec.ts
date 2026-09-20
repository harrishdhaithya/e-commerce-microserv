import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { httpErrorInterceptor } from '../../core/http-error.interceptor';
import { Account } from './account';
import { Address, Customer } from './account.model';

const CUSTOMER: Customer = {
  id: 'cust-1',
  email: 'customer@test.local',
  firstName: 'Test',
  lastName: 'Customer',
  phone: null,
  marketingOptIn: false,
  loyaltyTier: 'STANDARD',
  createdAt: '2026-09-20T12:00:00Z',
};

const ADDRESS: Address = {
  id: 'addr-1',
  label: 'Home',
  recipientName: 'Test Customer',
  line1: '1 Alice Street',
  line2: null,
  city: 'London',
  region: null,
  postalCode: 'SW1A 1AA',
  countryCode: 'GB',
  phone: null,
  defaultShipping: true,
  defaultBilling: false,
};

describe('Account', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Account],
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createComponent() {
    const fixture = TestBed.createComponent(Account);
    http.expectOne((r) => r.url === '/api/customers/me' && r.method === 'GET').flush(CUSTOMER);
    http.expectOne((r) => r.url === '/api/customers/me/addresses').flush([ADDRESS]);
    return fixture;
  }

  it('loads the profile and addresses', async () => {
    const fixture = createComponent();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('customer@test.local');
    expect(element.textContent).toContain('1 Alice Street');
    expect(element.querySelector('.badge')?.textContent).toContain('Default shipping');
  });

  it('sends a PATCH when saving the profile', async () => {
    const fixture = createComponent();
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('.form')!
      .dispatchEvent(new Event('submit'));

    const request = http.expectOne((r) => r.url === '/api/customers/me' && r.method === 'PATCH');
    expect(request.request.body.firstName).toBe('Test');
    request.flush(CUSTOMER);
  });

  it('shows validation messages next to the offending field', async () => {
    const fixture = createComponent();
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('.form')!
      .dispatchEvent(new Event('submit'));

    // The shared ApiError shape, with a violations array.
    http.expectOne((r) => r.method === 'PATCH').flush(
      {
        timestamp: '2026-09-20T12:00:00Z',
        status: 400,
        error: 'Bad Request',
        message: 'Validation failed',
        path: '/api/customers/me',
        correlationId: 'trace-1',
        violations: [{ field: 'firstName', message: 'size must be between 0 and 100' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.field__error')?.textContent).toContain('size must be between');
  });

  it('reloads the whole list after changing the default address', async () => {
    const fixture = createComponent();
    await fixture.whenStable();

    const buttons = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('.link'),
    );
    buttons.find((b) => b.textContent?.includes('Make default billing'))!.click();

    http.expectOne((r) => r.url === '/api/customers/me/addresses/addr-1/default-billing')
      .flush({ ...ADDRESS, defaultBilling: true });

    // A refetch, not a local patch: the server clears the previous default, so other
    // rows may have changed too.
    http.expectOne((r) => r.url === '/api/customers/me/addresses')
      .flush([{ ...ADDRESS, defaultBilling: true }]);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Default billing');
  });
});
