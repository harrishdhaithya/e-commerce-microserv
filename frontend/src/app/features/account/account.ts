import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ApiRequestError } from '../../core/http-error.interceptor';
import { AccountService } from './account.service';
import { Address, AddressInput, Customer } from './account.model';

function emptyAddress(): AddressInput {
  return {
    label: '',
    recipientName: '',
    line1: '',
    line2: '',
    city: '',
    region: '',
    postalCode: '',
    countryCode: '',
    phone: '',
    defaultShipping: false,
    defaultBilling: false,
  };
}

@Component({
  selector: 'app-account',
  imports: [FormsModule],
  templateUrl: './account.html',
  styleUrl: './account.scss',
})
export class Account {
  private readonly accounts = inject(AccountService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly profile = signal<Customer | null>(null);
  protected readonly addresses = signal<Address[]>([]);
  protected readonly loading = signal(true);
  protected readonly errorMessage = signal<string | null>(null);
  protected readonly correlationId = signal<string | null>(null);
  protected readonly savedMessage = signal<string | null>(null);

  /** Per-field validation messages from the API's ApiError.violations. */
  protected readonly fieldErrors = signal<Record<string, string>>({});

  protected firstName = '';
  protected lastName = '';
  protected phone = '';
  protected marketingOptIn = false;

  protected readonly showAddressForm = signal(false);
  protected readonly editingId = signal<string | null>(null);
  protected draft: AddressInput = emptyAddress();

  constructor() {
    this.loadProfile();
    this.loadAddresses();
  }

  // ---------------------------------------------------------------- profile

  private loadProfile(): void {
    this.accounts
      .getProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.applyProfile(customer);
          this.loading.set(false);
        },
        error: (error: ApiRequestError) => {
          this.fail(error);
          this.loading.set(false);
        },
      });
  }

  private applyProfile(customer: Customer): void {
    this.profile.set(customer);
    this.firstName = customer.firstName ?? '';
    this.lastName = customer.lastName ?? '';
    this.phone = customer.phone ?? '';
    this.marketingOptIn = customer.marketingOptIn;
  }

  protected saveProfile(): void {
    this.clearFeedback();
    this.accounts
      // Sends every field, so clearing one works. The API treats null as
      // "unchanged", not empty - so empty strings are what blank a field.
      .updateProfile({
        firstName: this.firstName,
        lastName: this.lastName,
        phone: this.phone,
        marketingOptIn: this.marketingOptIn,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (customer) => {
          this.applyProfile(customer);
          this.savedMessage.set('Profile saved');
        },
        error: (error: ApiRequestError) => this.fail(error),
      });
  }

  // -------------------------------------------------------------- addresses

  private loadAddresses(): void {
    this.accounts
      .listAddresses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (list) => this.addresses.set(list),
        error: (error: ApiRequestError) => this.fail(error),
      });
  }

  protected startCreate(): void {
    this.clearFeedback();
    this.draft = emptyAddress();
    this.editingId.set(null);
    this.showAddressForm.set(true);
  }

  protected startEdit(address: Address): void {
    this.clearFeedback();
    this.draft = {
      label: address.label ?? '',
      recipientName: address.recipientName,
      line1: address.line1,
      line2: address.line2 ?? '',
      city: address.city,
      region: address.region ?? '',
      postalCode: address.postalCode,
      countryCode: address.countryCode,
      phone: address.phone ?? '',
      defaultShipping: address.defaultShipping,
      defaultBilling: address.defaultBilling,
    };
    this.editingId.set(address.id);
    this.showAddressForm.set(true);
  }

  protected cancelAddressForm(): void {
    this.showAddressForm.set(false);
    this.editingId.set(null);
    this.clearFeedback();
  }

  protected saveAddress(): void {
    this.clearFeedback();
    const id = this.editingId();
    const request = id
      ? this.accounts.updateAddress(id, this.draft)
      : this.accounts.createAddress(this.draft);

    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.showAddressForm.set(false);
        this.editingId.set(null);
        this.savedMessage.set(id ? 'Address updated' : 'Address added');
        this.loadAddresses();
      },
      error: (error: ApiRequestError) => this.fail(error),
    });
  }

  protected deleteAddress(address: Address): void {
    this.clearFeedback();
    this.accounts
      .deleteAddress(address.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.savedMessage.set('Address removed');
          this.loadAddresses();
        },
        error: (error: ApiRequestError) => this.fail(error),
      });
  }

  protected makeDefaultShipping(address: Address): void {
    this.clearFeedback();
    this.accounts
      .setDefaultShipping(address.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        // Reload the whole list rather than patching one item: the server clears the
        // previous default, so any other address may have changed too.
        next: () => this.loadAddresses(),
        error: (error: ApiRequestError) => this.fail(error),
      });
  }

  protected makeDefaultBilling(address: Address): void {
    this.clearFeedback();
    this.accounts
      .setDefaultBilling(address.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loadAddresses(),
        error: (error: ApiRequestError) => this.fail(error),
      });
  }

  // ----------------------------------------------------------------- shared

  protected fieldError(field: string): string | undefined {
    return this.fieldErrors()[field];
  }

  private clearFeedback(): void {
    this.errorMessage.set(null);
    this.correlationId.set(null);
    this.savedMessage.set(null);
    this.fieldErrors.set({});
  }

  private fail(error: ApiRequestError): void {
    this.errorMessage.set(error.message);
    this.correlationId.set(error.correlationId);

    // Turn the shared ApiError's violations array into per-field messages, so a
    // rejected country code lands under the country field rather than in a banner.
    const byField: Record<string, string> = {};
    for (const violation of error.violations) {
      byField[violation.field] = violation.message;
    }
    this.fieldErrors.set(byField);
  }
}
