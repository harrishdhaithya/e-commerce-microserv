import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { ApiRequestError } from '../../../core/http-error.interceptor';
import { CatalogService } from '../catalog.service';
import { Category, Product, ProductSort } from '../product.model';

@Component({
  selector: 'app-product-list',
  imports: [FormsModule, CurrencyPipe],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly catalog = inject(CatalogService);

  // Declared before the constructor runs, because reload() uses it on first load.
  // Field initializers execute top to bottom - a DestroyRef declared at the bottom
  // of the class would still be undefined here.
  private readonly destroyRef = inject(DestroyRef);

  /**
   * State as signals. This app is zoneless (Angular 21 default - note there is no
   * zone.js dependency), so signals are what tell the framework something changed.
   * Mutating a plain field would not schedule a re-render.
   */
  protected readonly products = signal<Product[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(false);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly correlationId = signal<string | null>(null);

  protected readonly page = signal(0);
  protected readonly totalPages = signal(0);
  protected readonly totalElements = signal(0);
  protected readonly isFirst = signal(true);
  protected readonly isLast = signal(true);

  protected searchTerm = '';
  protected selectedCategory = '';
  protected selectedSort: ProductSort = 'createdAt,desc';

  /** Kept below the seed data count so pagination is actually exercised in dev. */
  private readonly pageSize = 8;

  protected readonly sortOptions: ReadonlyArray<{ value: ProductSort; label: string }> = [
    { value: 'createdAt,desc', label: 'Newest first' },
    { value: 'name,asc', label: 'Name (A-Z)' },
    { value: 'price,asc', label: 'Price (low to high)' },
    { value: 'price,desc', label: 'Price (high to low)' },
  ];

  /**
   * Keystrokes go through here rather than straight to the API.
   *
   * debounceTime waits for a pause in typing; distinctUntilChanged drops repeats
   * (holding backspace back to an already-seen value, for instance). Without these,
   * typing "keyboard" is eight requests, seven of them wasted and any of which can
   * land out of order.
   */
  private readonly searchInput = new Subject<string>();

  constructor() {
    this.searchInput
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload({ resetPage: true }));

    this.loadCategories();
    this.reload();
  }

  protected onSearchInput(value: string): void {
    this.searchTerm = value;
    this.searchInput.next(value);
  }

  protected onCategoryChange(slug: string): void {
    this.selectedCategory = slug;
    this.reload({ resetPage: true });
  }

  protected onSortChange(sort: ProductSort): void {
    this.selectedSort = sort;
    this.reload({ resetPage: true });
  }

  protected goToPage(page: number): void {
    if (page < 0 || (this.totalPages() > 0 && page >= this.totalPages())) {
      return;
    }
    this.page.set(page);
    this.reload();
  }

  protected clearFilters(): void {
    this.searchTerm = '';
    this.selectedCategory = '';
    this.selectedSort = 'createdAt,desc';
    this.reload({ resetPage: true });
  }

  protected hasActiveFilters(): boolean {
    return this.searchTerm.trim() !== '' || this.selectedCategory !== '';
  }

  protected reload(options: { resetPage?: boolean } = {}): void {
    if (options.resetPage) {
      this.page.set(0);
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.correlationId.set(null);

    this.catalog
      .listProducts({
        q: this.searchTerm,
        // '' is the "All categories" option; the API wants the param omitted.
        category: this.selectedCategory || null,
        page: this.page(),
        size: this.pageSize,
        sort: this.selectedSort,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.products.set(result.content);
          this.page.set(result.page);
          this.totalPages.set(result.totalPages);
          this.totalElements.set(result.totalElements);
          this.isFirst.set(result.first);
          this.isLast.set(result.last);
          this.loading.set(false);
        },
        error: (error: ApiRequestError) => {
          this.products.set([]);
          this.errorMessage.set(error.message);
          this.correlationId.set(error.correlationId);
          this.loading.set(false);
        },
      });
  }

  private loadCategories(): void {
    this.catalog
      .listCategories()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // A failed category list should not blank the page - the product grid still
        // works without the filter.
        next: (categories) => this.categories.set(categories),
        error: () => this.categories.set([]),
      });
  }
}
