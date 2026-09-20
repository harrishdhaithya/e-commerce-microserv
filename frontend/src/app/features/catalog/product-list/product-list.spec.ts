import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PagedResponse } from '../../../core/models/paged-response';
import { httpErrorInterceptor } from '../../../core/http-error.interceptor';
import { Category, Product } from '../product.model';
import { ProductList } from './product-list';

function pageOf(products: Product[]): PagedResponse<Product> {
  return {
    content: products,
    page: 0,
    size: 8,
    totalElements: products.length,
    totalPages: 1,
    first: true,
    last: true,
  };
}

const KEYBOARD: Product = {
  id: 'a1b2c3d4-0005-4e5f-8a9b-000000000005',
  sku: 'KBD-0001',
  name: 'Tactile 87 Mechanical Keyboard',
  description: 'Tenkeyless, hot-swappable switches.',
  price: 179,
  categoryName: 'Keyboards',
  categorySlug: 'keyboards',
  imageUrl: null,
};

const CATEGORIES: Category[] = [
  { id: 'c-1', name: 'Keyboards', slug: 'keyboards' },
  { id: 'c-2', name: 'Monitors', slug: 'monitors' },
];

describe('ProductList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductList],
      providers: [
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads products and categories on creation', async () => {
    const fixture = TestBed.createComponent(ProductList);

    http.expectOne((r) => r.url === '/api/categories').flush(CATEGORIES);
    const request = http.expectOne((r) => r.url === '/api/products');

    // Defaults the API contract promises.
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('8');
    expect(request.request.params.get('sort')).toBe('createdAt,desc');
    // Unset filters must be omitted, not sent blank.
    expect(request.request.params.has('q')).toBe(false);
    expect(request.request.params.has('category')).toBe(false);

    request.flush(pageOf([KEYBOARD]));
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.card__name')?.textContent).toContain('Tactile 87');
    expect(element.querySelector('.card__price')?.textContent).toContain('179');
  });

  it('shows the error message and correlation id when the API fails', async () => {
    const fixture = TestBed.createComponent(ProductList);

    http.expectOne((r) => r.url === '/api/categories').flush(CATEGORIES);
    http.expectOne((r) => r.url === '/api/products').flush(
      {
        timestamp: '2026-09-19T12:00:00Z',
        status: 500,
        error: 'Internal Server Error',
        message: 'An unexpected error occurred',
        path: '/api/products',
        correlationId: 'trace-abc-123',
        violations: [],
      },
      { status: 500, statusText: 'Internal Server Error' },
    );
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.state--error')).toBeTruthy();
    expect(element.textContent).toContain('An unexpected error occurred');
    // The correlation ID has to surface in the UI - it is how a user-reported
    // failure gets matched to a log line.
    expect(element.textContent).toContain('trace-abc-123');
  });

  it('reports a 5xx with no ApiError body as an unreachable service', async () => {
    // What actually happens when catalog-service is stopped: the Angular dev-server
    // proxy answers with a 500 and an HTML/text body, not a status 0 and not our
    // JSON error shape. Without this branch the UI showed Angular's raw
    // "Http failure response for http://localhost:4200/api/products?..." string.
    const fixture = TestBed.createComponent(ProductList);

    http.expectOne((r) => r.url === '/api/categories').flush(CATEGORIES);
    http
      .expectOne((r) => r.url === '/api/products')
      .flush('Error occurred while trying to proxy: localhost:4200/api/products', {
        status: 500,
        statusText: 'Internal Server Error',
      });
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Cannot reach the API');
    // The raw URL must not leak into the UI.
    expect(element.textContent).not.toContain('Http failure response');
  });

  it('reports an unreachable API distinctly from a server error', async () => {
    const fixture = TestBed.createComponent(ProductList);

    http.expectOne((r) => r.url === '/api/categories').flush(CATEGORIES);
    http
      .expectOne((r) => r.url === '/api/products')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Cannot reach the API');
    expect(element.textContent).toContain('8082');
  });

  it('shows an empty state when nothing matches', async () => {
    const fixture = TestBed.createComponent(ProductList);

    http.expectOne((r) => r.url === '/api/categories').flush(CATEGORIES);
    http.expectOne((r) => r.url === '/api/products').flush(pageOf([]));
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('No products match those filters');
  });

  it('still renders products when the category list fails', async () => {
    const fixture = TestBed.createComponent(ProductList);

    http
      .expectOne((r) => r.url === '/api/categories')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });
    http.expectOne((r) => r.url === '/api/products').flush(pageOf([KEYBOARD]));
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.card__name')).toBeTruthy();
    expect(element.querySelector('.state--error')).toBeNull();
  });
});
