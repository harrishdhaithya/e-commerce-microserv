/** Mirror of `com.ecommerce.customer.api.dto.CustomerResponse`. */
export interface Customer {
  id: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  marketingOptIn: boolean;
  loyaltyTier: string;
  createdAt: string;
}

/**
 * Mirror of `UpdateCustomerRequest`.
 *
 * Every field optional, because the endpoint is a PATCH: omitting a field leaves it
 * unchanged. Sending `marketingOptIn: undefined` is meaningfully different from
 * sending `false`.
 */
export interface UpdateCustomer {
  firstName?: string;
  lastName?: string;
  phone?: string;
  marketingOptIn?: boolean;
}

/** Mirror of `com.ecommerce.customer.api.dto.AddressResponse`. */
export interface Address {
  id: string;
  label: string | null;
  recipientName: string;
  line1: string;
  line2: string | null;
  city: string;
  region: string | null;
  postalCode: string;
  countryCode: string;
  phone: string | null;
  defaultShipping: boolean;
  defaultBilling: boolean;
}

/** Mirror of `AddressRequest`. A full representation - the endpoint is a PUT. */
export interface AddressInput {
  label?: string;
  recipientName: string;
  line1: string;
  line2?: string;
  city: string;
  region?: string;
  postalCode: string;
  countryCode: string;
  phone?: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
}
