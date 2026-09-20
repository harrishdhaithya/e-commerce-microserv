import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api.config';
import { PagedResponse } from '../../core/models/paged-response';
import { Category, Product, ProductQuery } from './product.model';

@Injectable({ providedIn: 'root' })
export class CatalogService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  listProducts(query: ProductQuery): Observable<PagedResponse<Product>> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', query.sort);

    // Only send filters that are actually set. The backend treats a missing or
    // blank param as "no filter", but leaving them out keeps the URL readable and
    // the browser's cache keys stable.
    if (query.q.trim()) {
      params = params.set('q', query.q.trim());
    }
    if (query.category) {
      params = params.set('category', query.category);
    }

    return this.http.get<PagedResponse<Product>>(`${this.baseUrl}/products`, { params });
  }

  getProduct(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.baseUrl}/products/${id}`);
  }

  listCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.baseUrl}/categories`);
  }
}
