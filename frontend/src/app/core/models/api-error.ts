/**
 * Mirror of `com.ecommerce.common.web.ApiError`.
 *
 * Because every service shares that error shape, this one interface covers the
 * whole platform - and the interceptor that reads it never needs a per-service
 * branch. That is the payoff of putting the error model in `common-web`.
 *
 * In Phase 1 these types get generated from each service's OpenAPI document
 * instead of hand-written; until then keep them in step with the Java records.
 */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId: string;
  violations: FieldViolation[];
}

export interface FieldViolation {
  field: string;
  message: string;
}
