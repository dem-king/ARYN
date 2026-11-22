export default {
  afterSales: {
    submit: {
      navTitle: 'Request Refund',
      refundProduct: 'Refund Item',
      serviceType: 'Service Type',
      serviceTypeTitle: 'Select Service Type',
      serviceTypePlaceholder: 'Select a service type',
      refundOnly: 'Refund Only',
      returnRefund: 'Return & Refund',
      refundAmount: 'Refund Amount',
      returnQty: 'Return Qty',
      refundReason: 'Refund Reason',
      refundReasonPlaceholder: 'Enter the refund reason',
      submit: 'Submit Request',
      confirmSubmit: 'Submit this request?',
      selectServiceType: 'Please select a service type',
    },
    list: {
      navTitle: 'Refunds & After-sales',
      refund: 'Refund',
      return: 'Return',
      applyQty: 'Qty: {count}',
      status: {
        pendingReview: 'Refund request submitted. Waiting for the seller to review.',
        awaitingReturn: 'Seller approved the return. Please ship the item back soon.',
        returnProcessing: 'Return is being processed. Please wait.',
        returnCompleted: 'Item returned. Waiting for the seller to confirm.',
        refundProcessing: 'Refund is being processed. Please wait.',
        refundSuccess: 'Refund completed. The money will be returned via the original payment method.',
        returnFailed: 'Return failed. Please contact the seller.',
        refundFailed: 'Refund failed. Please contact the seller.',
      },
    },
    detail: {
      navTitle: 'Refund Details',
      applyQty: 'Qty: {count}',
      refundAmount: 'Refund Amount',
      applyTime: 'Applied At',
      applyReason: 'Reason',
      refundMethod: 'Refund Method',
      originalPayment: 'Original payment method',
      receivingAccount: 'Receiving Account',
      contactSupport: 'Contact Support',
      refundRequest: 'Refund Request',
      fallbackTitle: 'Processing',
      fallbackDesc: 'Please wait',
      status: {
        pendingReview: {
          title: 'Under Review',
          desc: 'Waiting for the seller to handle it',
        },
        awaitingReturn: {
          title: 'Return Pending',
          desc: 'Seller approved the return. Please ship the item back soon.',
        },
        returnProcessing: {
          title: 'Return Processing',
          desc: 'Return is being processed. Please wait.',
        },
        returnCompleted: {
          title: 'Return Completed',
          desc: 'Item returned. Waiting for the seller to confirm.',
        },
        refundProcessing: {
          title: 'Refund Processing',
          desc: 'Refund is being processed. Please wait.',
        },
        refundSuccess: {
          title: 'Refund Successful',
          desc: 'Refund completed. The money will be returned via the original payment method.',
        },
        returnFailed: {
          title: 'Return Failed',
          desc: 'Return failed. Please contact the seller.',
        },
        refundFailed: {
          title: 'Refund Failed',
          desc: 'Refund failed. Please contact the seller.',
        },
      },
    },
  },
}
