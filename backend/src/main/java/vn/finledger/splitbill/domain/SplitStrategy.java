package vn.finledger.splitbill.domain;

/**
 * Strategy defining how an expense is allocated across group members.
 */
public enum SplitStrategy {
    EQUAL,
    EXACT,
    PERCENTAGE,
    SHARES
}
