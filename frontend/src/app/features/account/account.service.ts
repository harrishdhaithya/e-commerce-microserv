import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../../core/api.config';
import { Address, AddressInput, Customer, UpdateCustomer } from './account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = inject(API_BASE_URL);

  private get customersUrl(): string {
    return `${this.baseUrl}/customers`;
  }

  private get addressesUrl(): string {
    return `${this.customersUrl}/me/addresses`;
  }

  /**
   * The signed-in customer. Creates the record server-side on first call, so there
   * is no registration step in this app - Keycloak owns who exists.
   */
  getProfile(): Observable<Customer> {
    return this.http.get<Customer>(`${this.customersUrl}/me`);
  }

  updateProfile(changes: UpdateCustomer): Observable<Customer> {
    return this.http.patch<Customer>(`${this.customersUrl}/me`, changes);
  }

  listAddresses(): Observable<Address[]> {
    return this.http.get<Address[]>(this.addressesUrl);
  }

  createAddress(address: AddressInput): Observable<Address> {
    return this.http.post<Address>(this.addressesUrl, address);
  }

  updateAddress(id: string, address: AddressInput): Observable<Address> {
    return this.http.put<Address>(`${this.addressesUrl}/${id}`, address);
  }

  deleteAddress(id: string): Observable<void> {
    return this.http.delete<void>(`${this.addressesUrl}/${id}`);
  }

  setDefaultShipping(id: string): Observable<Address> {
    return this.http.put<Address>(`${this.addressesUrl}/${id}/default-shipping`, {});
  }

  setDefaultBilling(id: string): Observable<Address> {
    return this.http.put<Address>(`${this.addressesUrl}/${id}/default-billing`, {});
  }
}
