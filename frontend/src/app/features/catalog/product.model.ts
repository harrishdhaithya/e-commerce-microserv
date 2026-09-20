/** Mirror of `com.ecommerce.catalog.api.dto.ProductResponse`. */
export interface Product {
  /** The backend's public_id. Internal database ids are never exposed. */
  id: string;
  sku: string;
  name: string;
  description: string | null;
  price: number;
  categoryName: string;
  categorySlug: string;
  imageUrl: string | null;
}

/** Mirror of `com.ecommerce.catalog.api.dto.CategoryResponse`. */
export interface Category {
  id: string;
  name: string;
  slug: string;
}

/** Sort options the backend allowlist accepts - see ProductController.parseSort. */
export type ProductSort = 'createdAt,desc' | 'name,asc' | 'price,asc' | 'price,desc';

export interface ProductQuery {
  q: string;
  category: string | null;
  page: number;
  size: number;
  sort: ProductSort;
}
