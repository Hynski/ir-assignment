function [ precision1, precision2 ] = avgprecision( set1, set2, set3, set4 )
%CALC Calculates the averaged precision values for recall = 0:0.1:1

% initialization
% 1~bm25, 2~tf-idf
precision1=zeros(1,11);
precision2=zeros(1,11);

for j=1:4
    
    data1 = set1;
    if j == 2
        data1 = set2;
    elseif j == 3
        data1 = set3;
    elseif j == 4
        data1 = set4;
    end
    
    k = 0;
    for i=1:size(data1,1)/2
        % find precision values 0:0.1:1
        if k > 10
            break
        elseif data1(i,4) >= k/10
            precision1(1+k) = ((j-1)*precision1(1+k)+data1(i,3))/j;
            k = k+1;
        end
    end

    k = 0;
    for i=size(data1,1)/2+1:size(data1,1)
        if k > 10
            k = 0;
            break
        elseif data1(i,4) >= k/10
            precision2(1+k) = ((j-1)*precision2(1+k)+data1(i,3))/j;
            k = k+1;
        end
    end
    
end

end

